package com.paximum.paxassist.guard;

import java.time.Duration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import com.paximum.paxassist.guard.config.GuardProperties;

import io.micrometer.core.instrument.MeterRegistry;

/**
 * Stateful guard rule: temporarily blocks a user who sends too many <b>consecutive</b> out-of-scope
 * (OTHER intent) messages. Unlike {@link GuardRuleService} (stateless regex on a single message),
 * this needs a per-user counter, so it is Redis-backed via {@link StringRedisTemplate} — the same
 * {@code INCR}/{@code EXPIRE} + namespaced-prefix pattern used by
 * {@code reservation.pending.PendingReservationStore}.
 *
 * <p><b>Semantics</b>:
 * <ul>
 *   <li>Each OTHER turn increments {@code guard:oos:streak:{userId}} and refreshes its TTL to
 *       {@code windowSeconds} (a longer gap resets the streak).</li>
 *   <li>Any non-OTHER turn deletes the streak key — only <em>consecutive</em> OTHER counts.</li>
 *   <li>When the streak reaches {@code threshold}, {@code guard:oos:block:{userId}} is set with a
 *       {@code blockDurationSeconds} TTL and a {@link GuardBlockedException} is thrown so the
 *       triggering turn already returns the block message. Every subsequent message is rejected up
 *       front by {@link #assertNotBlocked} (before any LLM call) until the block TTL expires.</li>
 * </ul>
 *
 * <p><b>Fail-open</b>: mirrors the rate limiter — a Redis outage never blocks a legitimate user
 * (when {@code failOpen} is true it logs and lets the request through, counting nothing) and
 * increments the {@code guard.fail_open} counter, so an outage that leaves the guard unenforced is
 * visible in metrics rather than only in the logs. A {@link GuardBlockedException} is a real block
 * decision, so it is always re-thrown, never swallowed.
 */
@Component
public class OutOfScopeGuard {

    private static final Logger log = LoggerFactory.getLogger(OutOfScopeGuard.class);

    private static final String STREAK_KEY_PREFIX = "guard:oos:streak:";
    private static final String BLOCK_KEY_PREFIX = "guard:oos:block:";

    static final String BLOCK_MESSAGE =
            "Çok fazla konu dışı istek gönderdin. Lütfen bir süre sonra tekrar dene.";
    private static final String BLOCK_REASON = "Out-of-scope abuse: user temporarily blocked";

    private final StringRedisTemplate redis;
    private final GuardProperties.OutOfScope config;
    private final MeterRegistry meterRegistry;

    public OutOfScopeGuard(StringRedisTemplate redis, GuardProperties properties, MeterRegistry meterRegistry) {
        this.redis = redis;
        this.config = properties.outOfScope();
        this.meterRegistry = meterRegistry;
    }

    /** Rejects a user currently under a temporary block, before any downstream (LLM) call is made. */
    public void assertNotBlocked(Long userId) {
        if (disabled() || userId == null) {
            return;
        }
        Boolean blocked;
        try {
            blocked = redis.hasKey(BLOCK_KEY_PREFIX + userId);
        } catch (RuntimeException e) {
            failOpen("block check", e);
            return;
        }
        if (Boolean.TRUE.equals(blocked)) {
            throw new GuardBlockedException(BLOCK_MESSAGE, BLOCK_REASON);
        }
    }

    /**
     * Records the classified intent of the current turn. Increments the consecutive-OTHER streak
     * when {@code isOutOfScope}, resets it otherwise; throws {@link GuardBlockedException} on the turn
     * the streak reaches the configured threshold.
     */
    public void registerOutOfScope(Long userId, boolean isOutOfScope) {
        if (disabled() || userId == null) {
            return;
        }
        String streakKey = STREAK_KEY_PREFIX + userId;
        try {
            if (!isOutOfScope) {
                redis.delete(streakKey);
                return;
            }
            Long count = redis.opsForValue().increment(streakKey);
            redis.expire(streakKey, Duration.ofSeconds(config.windowSeconds()));
            if (count != null && count >= config.threshold()) {
                redis.opsForValue().set(BLOCK_KEY_PREFIX + userId, "1",
                        Duration.ofSeconds(config.blockDurationSeconds()));
                redis.delete(streakKey);
                throw new GuardBlockedException(BLOCK_MESSAGE, BLOCK_REASON);
            }
        } catch (GuardBlockedException e) {
            // A real block decision — never swallowed by fail-open.
            throw e;
        } catch (RuntimeException e) {
            failOpen("streak update", e);
        }
    }

    private boolean disabled() {
        return config == null || !config.enabled();
    }

    private void failOpen(String op, RuntimeException e) {
        if (config.failOpen()) {
            log.warn("OutOfScopeGuard {} failed; failing open: {}", op, e.getMessage());
            // Tag with the operation (a fixed set of two) so cardinality stays bounded.
            meterRegistry.counter("guard.fail_open", "operation", op).increment();
            return;
        }
        throw e;
    }
}
