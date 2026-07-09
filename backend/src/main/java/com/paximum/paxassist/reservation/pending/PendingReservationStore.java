package com.paximum.paxassist.reservation.pending;

import java.time.Duration;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

/**
 * Redis-backed storage for the two mutable "pending reservation" states — deliberately a plain
 * {@link StringRedisTemplate} mechanism, NOT {@code @Cacheable}: we need explicit write / read /
 * atomic-claim / expire of a mutable object, not read-through caching of a method's return value.
 *
 * <p><b>Key design</b> (documented):
 * <ul>
 *   <li>Keys: {@code reservation:preview:{previewId}} and {@code reservation:pending-commit:{token}}
 *       (UUIDs) — namespaced so they never collide with the {@code @Cacheable} hotel/flight caches.</li>
 *   <li>Serialization: JSON via a self-contained {@link ObjectMapper} (JavaTimeModule, ISO dates) so
 *       {@code LocalDate}/{@code OffsetDateTime}/{@code BigDecimal} round-trip cleanly and independently
 *       of the global app ObjectMapper.</li>
 *   <li>TTL: configurable ({@code app.reservation.preview-ttl-minutes}, default 15;
 *       {@code app.reservation.confirm-ttl-minutes}, default 10). Redis enforces expiry.</li>
 *   <li><b>Atomic claim</b>: {@link #claimPreview}/{@link #claimAwaitingCommit} use
 *       {@code getAndDelete} (Redis {@code GETDEL}, atomic). Because Redis is single-threaded, only ONE
 *       concurrent caller can receive the value; every duplicate gets empty. This is the duplicate /
 *       concurrent-purchase guard.</li>
 * </ul>
 */
@Component
public class PendingReservationStore {

    private static final String PREVIEW_PREFIX = "reservation:preview:";
    private static final String AWAITING_PREFIX = "reservation:pending-commit:";

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final Duration previewTtl;
    private final Duration awaitingTtl;

    public PendingReservationStore(
            StringRedisTemplate redis,
            @Value("${app.reservation.preview-ttl-minutes:15}") long previewTtlMinutes,
            @Value("${app.reservation.confirm-ttl-minutes:10}") long confirmTtlMinutes) {
        this.redis = redis;
        this.objectMapper = JsonMapper.builder()
                .addModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .build();
        this.previewTtl = Duration.ofMinutes(previewTtlMinutes);
        this.awaitingTtl = Duration.ofMinutes(confirmTtlMinutes);
    }

    public Duration previewTtl() {
        return previewTtl;
    }

    // --- Preview snapshot --------------------------------------------------------------------

    public void savePreview(PendingReservation preview) {
        redis.opsForValue().set(PREVIEW_PREFIX + preview.previewId(), toJson(preview), previewTtl);
    }

    /** Non-destructive read (GET) — used to pre-check ownership without consuming the snapshot. */
    public Optional<PendingReservation> peekPreview(String previewId) {
        return read(redis.opsForValue().get(PREVIEW_PREFIX + previewId), PendingReservation.class);
    }

    /** Atomic claim (GETDEL): returns the snapshot and removes it in one operation. The concurrency guard. */
    public Optional<PendingReservation> claimPreview(String previewId) {
        return read(redis.opsForValue().getAndDelete(PREVIEW_PREFIX + previewId), PendingReservation.class);
    }

    // --- Awaiting-commit (post setReservationInfo warning) -----------------------------------

    public void saveAwaitingCommit(AwaitingCommit awaiting) {
        redis.opsForValue().set(AWAITING_PREFIX + awaiting.confirmationToken(), toJson(awaiting), awaitingTtl);
    }

    public Optional<AwaitingCommit> peekAwaitingCommit(String token) {
        return read(redis.opsForValue().get(AWAITING_PREFIX + token), AwaitingCommit.class);
    }

    /** Atomic claim (GETDEL): guarantees at most one second-confirm reaches commitTransaction. */
    public Optional<AwaitingCommit> claimAwaitingCommit(String token) {
        return read(redis.opsForValue().getAndDelete(AWAITING_PREFIX + token), AwaitingCommit.class);
    }

    // --- serialization -----------------------------------------------------------------------

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize pending reservation state", e);
        }
    }

    private <T> Optional<T> read(String json, Class<T> type) {
        if (json == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(objectMapper.readValue(json, type));
        } catch (Exception e) {
            throw new IllegalStateException("Failed to deserialize pending reservation state", e);
        }
    }
}
