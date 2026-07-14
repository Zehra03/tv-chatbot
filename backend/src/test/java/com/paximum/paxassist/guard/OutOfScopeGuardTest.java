package com.paximum.paxassist.guard;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import com.paximum.paxassist.guard.config.GuardProperties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OutOfScopeGuardTest {

    private static final Long USER = 42L;
    private static final String STREAK_KEY = "guard:oos:streak:42";
    private static final String BLOCK_KEY = "guard:oos:block:42";

    @Mock
    private StringRedisTemplate redis;
    @Mock
    private ValueOperations<String, String> valueOps;

    private static GuardProperties props(boolean enabled, boolean failOpen) {
        return new GuardProperties(new GuardProperties.OutOfScope(enabled, 5, 1800, 900, failOpen), null);
    }

    private OutOfScopeGuard guard(boolean enabled, boolean failOpen) {
        return new OutOfScopeGuard(redis, props(enabled, failOpen));
    }

    @Test
    void fifthConsecutiveOutOfScope_setsBlockKeyAndThrows() {
        OutOfScopeGuard guard = guard(true, true);
        when(redis.opsForValue()).thenReturn(valueOps);
        when(valueOps.increment(STREAK_KEY)).thenReturn(1L, 2L, 3L, 4L, 5L);

        // First four consecutive OTHER turns: counted, no block.
        for (int i = 0; i < 4; i++) {
            assertThatCode(() -> guard.registerOutOfScope(USER, true)).doesNotThrowAnyException();
        }
        verify(valueOps, never()).set(eq(BLOCK_KEY), any(), any());

        // Fifth crosses the threshold: block key set (15 min TTL), streak cleared, and it throws.
        assertThatThrownBy(() -> guard.registerOutOfScope(USER, true))
                .isInstanceOf(GuardBlockedException.class)
                .hasMessage(OutOfScopeGuard.BLOCK_MESSAGE);
        verify(valueOps).set(BLOCK_KEY, "1", Duration.ofSeconds(900));
        verify(redis).delete(STREAK_KEY);
    }

    @Test
    void belowThreshold_incrementsAndRefreshesWindow_noBlock() {
        OutOfScopeGuard guard = guard(true, true);
        when(redis.opsForValue()).thenReturn(valueOps);
        when(valueOps.increment(STREAK_KEY)).thenReturn(3L);

        assertThatCode(() -> guard.registerOutOfScope(USER, true)).doesNotThrowAnyException();

        verify(valueOps).increment(STREAK_KEY);
        verify(redis).expire(STREAK_KEY, Duration.ofSeconds(1800));
        verify(valueOps, never()).set(any(), any(), any());
    }

    @Test
    void inScopeTurn_resetsStreak() {
        OutOfScopeGuard guard = guard(true, true);

        guard.registerOutOfScope(USER, false);

        verify(redis).delete(STREAK_KEY);
        verifyNoInteractions(valueOps);
    }

    @Test
    void assertNotBlocked_throwsWhenBlockKeyPresent() {
        OutOfScopeGuard guard = guard(true, true);
        when(redis.hasKey(BLOCK_KEY)).thenReturn(true);

        assertThatThrownBy(() -> guard.assertNotBlocked(USER))
                .isInstanceOf(GuardBlockedException.class)
                .hasMessage(OutOfScopeGuard.BLOCK_MESSAGE);
    }

    @Test
    void assertNotBlocked_passesWhenNoBlockKey() {
        OutOfScopeGuard guard = guard(true, true);
        when(redis.hasKey(BLOCK_KEY)).thenReturn(false);

        assertThatCode(() -> guard.assertNotBlocked(USER)).doesNotThrowAnyException();
    }

    @Test
    void redisOutage_failsOpen_whenFailOpenTrue() {
        OutOfScopeGuard guard = guard(true, true);
        when(redis.opsForValue()).thenReturn(valueOps);
        when(valueOps.increment(STREAK_KEY)).thenThrow(new RedisConnectionFailureException("down"));

        // Fails open: neither throws nor blocks the user.
        assertThatCode(() -> guard.registerOutOfScope(USER, true)).doesNotThrowAnyException();
        verify(valueOps, never()).set(any(), any(), any());
    }

    @Test
    void redisOutage_propagates_whenFailOpenFalse() {
        OutOfScopeGuard guard = guard(true, false);
        when(redis.hasKey(BLOCK_KEY)).thenThrow(new RedisConnectionFailureException("down"));

        assertThatThrownBy(() -> guard.assertNotBlocked(USER))
                .isInstanceOf(RedisConnectionFailureException.class);
    }

    @Test
    void disabled_isNoOp() {
        OutOfScopeGuard guard = guard(false, true);

        assertThatCode(() -> {
            guard.assertNotBlocked(USER);
            guard.registerOutOfScope(USER, true);
            guard.registerOutOfScope(USER, false);
        }).doesNotThrowAnyException();
        verifyNoInteractions(redis);
    }

    @Test
    void nullUserId_isNoOp() {
        OutOfScopeGuard guard = guard(true, true);

        assertThatCode(() -> {
            guard.assertNotBlocked(null);
            guard.registerOutOfScope(null, true);
        }).doesNotThrowAnyException();
        verifyNoInteractions(redis);
    }
}
