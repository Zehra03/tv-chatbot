package com.paximum.paxassist.ratelimiter;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.redis.lettuce.cas.LettuceBasedProxyManager;
import io.lettuce.core.api.StatefulRedisConnection;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Proves the Bucket4j + Redis (Lettuce) infrastructure end-to-end against a real
 * Redis: build a bucket, consume until empty, and confirm the bucket key is
 * actually persisted in Redis. Skips gracefully when no Redis is reachable so it
 * never breaks builds in environments without one (CI provisions Redis).
 */
class RateLimitRedisConfigSmokeTest {

    private LettuceConnectionFactory connectionFactory;
    private StatefulRedisConnection<String, byte[]> connection;
    private LettuceBasedProxyManager<String> proxyManager;
    private final String bucketKey = "rl:smoke:" + UUID.randomUUID();

    @BeforeEach
    void setUp() {
        String host = System.getenv().getOrDefault("SPRING_REDIS_HOST", "localhost");
        int port = Integer.parseInt(System.getenv().getOrDefault("SPRING_REDIS_PORT", "6379"));

        connectionFactory = new LettuceConnectionFactory(new RedisStandaloneConfiguration(host, port));
        connectionFactory.afterPropertiesSet();
        connectionFactory.start();

        assumeTrue(redisReachable(), "Redis not reachable at " + host + ":" + port + " — skipping smoke test");

        RateLimitRedisConfig config = new RateLimitRedisConfig();
        connection = config.rateLimitRedisConnection(connectionFactory);
        proxyManager = config.rateLimitProxyManager(connection);
    }

    @Test
    void bucket_isEnforced_andKeyPersistsInRedis() {
        Bandwidth limit = Bandwidth.builder()
                .capacity(5)
                .refillGreedy(5, Duration.ofMinutes(1))
                .build();
        BucketConfiguration bucketConfig = BucketConfiguration.builder()
                .addLimit(limit)
                .build();

        Bucket bucket = proxyManager.builder().build(bucketKey, () -> bucketConfig);

        // Consume the full capacity — every token succeeds.
        for (int i = 0; i < 5; i++) {
            assertThat(bucket.tryConsume(1))
                    .as("token %d within capacity should be granted", i + 1)
                    .isTrue();
        }

        // Capacity exhausted — the next token is rejected.
        assertThat(bucket.tryConsume(1))
                .as("token beyond capacity should be denied")
                .isFalse();

        // The bucket state must actually live in Redis under our key.
        try (RedisConnection redis = connectionFactory.getConnection()) {
            Boolean exists = redis.keyCommands().exists(bucketKey.getBytes(StandardCharsets.UTF_8));
            assertThat(exists).as("bucket key should exist in Redis").isTrue();
        }
    }

    @AfterEach
    void tearDown() {
        if (connectionFactory != null && connectionFactory.isRunning()) {
            try (RedisConnection redis = connectionFactory.getConnection()) {
                redis.keyCommands().del(bucketKey.getBytes(StandardCharsets.UTF_8));
            } catch (RuntimeException ignored) {
                // best-effort cleanup
            }
        }
        if (connection != null) {
            connection.close();
        }
        if (connectionFactory != null) {
            connectionFactory.destroy();
        }
    }

    private boolean redisReachable() {
        try (RedisConnection redis = connectionFactory.getConnection()) {
            return "PONG".equalsIgnoreCase(redis.ping());
        } catch (RuntimeException e) {
            return false;
        }
    }
}
