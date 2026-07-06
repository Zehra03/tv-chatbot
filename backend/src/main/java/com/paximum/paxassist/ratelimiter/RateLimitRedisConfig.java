package com.paximum.paxassist.ratelimiter;

import io.github.bucket4j.distributed.ExpirationAfterWriteStrategy;
import io.github.bucket4j.redis.lettuce.Bucket4jLettuce;
import io.github.bucket4j.redis.lettuce.cas.LettuceBasedProxyManager;
import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisConnectionException;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.codec.ByteArrayCodec;
import io.lettuce.core.codec.RedisCodec;
import io.lettuce.core.codec.StringCodec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;

import java.time.Duration;

/**
 * Wires Bucket4j's distributed rate limiting onto the Redis client that already
 * backs the application cache. It deliberately does NOT create a new Redis client:
 * it borrows Spring's managed Lettuce {@link RedisClient} and opens a single
 * dedicated connection with the {@code <String, byte[]>} codec Bucket4j requires.
 *
 * <p>Beans are {@link Lazy} on purpose: nothing here is touched during application
 * startup, so a Redis outage at boot does not prevent the app from starting. The
 * connection is established on first use, where a {@link RedisConnectionException}
 * surfaces as a catchable exception — the hook a later fail-open filter will use.
 *
 * <p>This card only proves the infrastructure; no endpoint/user rate rules live here.
 */
@Configuration
@EnableConfigurationProperties(RateLimitProperties.class)
public class RateLimitRedisConfig {

    private static final Logger log = LoggerFactory.getLogger(RateLimitRedisConfig.class);

    /**
     * Extra time added on top of the computed refill-to-max duration before a bucket
     * key is allowed to expire, so a still-active bucket is never evicted mid-window.
     */
    private static final Duration TTL_MARGIN = Duration.ofSeconds(10);

    /**
     * A dedicated Lettuce connection for rate limiting, opened from the existing
     * Spring-managed {@link RedisClient}. Closed by Spring on shutdown.
     */
    @Bean(destroyMethod = "close")
    @Lazy
    public StatefulRedisConnection<String, byte[]> rateLimitRedisConnection(
            RedisConnectionFactory redisConnectionFactory) {

        if (!(redisConnectionFactory instanceof LettuceConnectionFactory lettuceFactory)) {
            throw new IllegalStateException(
                    "Rate limiting requires a LettuceConnectionFactory to reuse, but found: "
                            + redisConnectionFactory.getClass().getName());
        }

        Object nativeClient = lettuceFactory.getNativeClient();
        if (!(nativeClient instanceof RedisClient redisClient)) {
            throw new IllegalStateException(
                    "Expected a standalone Lettuce RedisClient to reuse, but found: "
                            + (nativeClient == null ? "null" : nativeClient.getClass().getName()));
        }

        try {
            // Reuse the existing client; only a new connection (String keys, byte[] values) is opened.
            return redisClient.connect(RedisCodec.of(StringCodec.UTF8, ByteArrayCodec.INSTANCE));
        } catch (RedisConnectionException e) {
            // @Lazy means this only runs on first use, never at startup. Surface it so the
            // future fail-open layer can catch it rather than letting it crash the caller silently.
            log.warn("Could not open rate-limit Redis connection; rate limiting is unavailable until Redis recovers", e);
            throw e;
        }
    }

    /**
     * Bucket4j proxy manager keyed by String. Bucket state is stored in Redis with a
     * refill-based TTL so unused keys expire instead of accumulating forever.
     */
    @Bean
    @Lazy
    public LettuceBasedProxyManager<String> rateLimitProxyManager(
            StatefulRedisConnection<String, byte[]> rateLimitRedisConnection) {

        return Bucket4jLettuce.casBasedBuilder(rateLimitRedisConnection)
                .expirationAfterWrite(
                        ExpirationAfterWriteStrategy.basedOnTimeForRefillingBucketUpToMax(TTL_MARGIN))
                .build();
    }
}
