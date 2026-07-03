package com.paximum.paxassist.ratelimiter;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Scenario 5 — fail-open. Runs the real app against a Testcontainers Redis, then stops
 * Redis and asserts that requests still succeed (200) and the {@code ratelimit.fail_open}
 * Micrometer counter increments. Verified via the MeterRegistry, not via Gatling.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("loadtest")
@Testcontainers
class RateLimiterFailOpenIntegrationTest {

    @Container
    static final GenericContainer<?> redis =
            new GenericContainer<>(DockerImageName.parse("redis:7.2-alpine")).withExposedPorts(6379);

    @DynamicPropertySource
    static void redisProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
    }

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private MeterRegistry meterRegistry;

    @Test
    void whenRedisUnreachable_requestsFailOpenAndCounterIncrements() {
        // Warm-up while Redis is up: the limiter works and returns 200.
        ResponseEntity<String> warmUp = restTemplate.getForEntity("/api/search/test", String.class);
        assertThat(warmUp.getStatusCode().value()).isEqualTo(200);

        double before = failOpenCount();

        // Redis becomes unreachable.
        redis.stop();

        // Requests must still succeed (fail-open) even though the limiter cannot reach Redis.
        for (int i = 0; i < 3; i++) {
            ResponseEntity<String> response = restTemplate.getForEntity("/api/search/test", String.class);
            assertThat(response.getStatusCode().value())
                    .as("request %d should pass through when failing open", i + 1)
                    .isEqualTo(200);
        }

        assertThat(failOpenCount())
                .as("ratelimit.fail_open counter should increment on Redis failure")
                .isGreaterThan(before);
    }

    private double failOpenCount() {
        return meterRegistry.find("ratelimit.fail_open").counters()
                .stream()
                .mapToDouble(Counter::count)
                .sum();
    }
}
