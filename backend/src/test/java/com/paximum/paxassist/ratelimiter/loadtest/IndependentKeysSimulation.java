package com.paximum.paxassist.ratelimiter.loadtest;

import io.gatling.javaapi.core.ScenarioBuilder;
import io.gatling.javaapi.core.Simulation;
import io.gatling.javaapi.http.HttpProtocolBuilder;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static io.gatling.javaapi.core.CoreDsl.*;
import static io.gatling.javaapi.http.HttpDsl.*;

/**
 * Scenario 2 — independent keys.
 *
 * <p>Many distinct clients (each a unique X-Forwarded-For, fed per virtual user) hit
 * the same endpoint concurrently. Each key gets its own {@code CAPACITY} successful
 * requests followed by one 429. If limits leaked across keys, a within-limit request
 * would return 429 and fail the check — so an all-green run proves isolation.
 */
public class IndependentKeysSimulation extends Simulation {

    private static final String BASE_URL = System.getProperty("baseUrl", "http://localhost:8080");
    private static final int CAPACITY = Integer.getInteger("capacity", 10);
    private static final int USERS = Integer.getInteger("users", 20);

    // Distinct client IP per virtual user.
    private final Iterator<Map<String, Object>> ipFeeder = new Iterator<>() {
        private final AtomicInteger counter = new AtomicInteger();

        @Override
        public boolean hasNext() {
            return true;
        }

        @Override
        public Map<String, Object> next() {
            Map<String, Object> record = new HashMap<>();
            record.put("clientIp", "10.1." + (counter.incrementAndGet()) + ".1");
            return record;
        }
    };

    private final HttpProtocolBuilder httpProtocol = http
            .baseUrl(BASE_URL)
            .acceptHeader("application/json");

    private final ScenarioBuilder scn = scenario("Independent keys")
            .feed(ipFeeder)
            .repeat(CAPACITY).on(
                    exec(http("own within limit").get("/api/search/test")
                            .header("X-Forwarded-For", "#{clientIp}")
                            .check(status().is(200))))
            .exec(http("own over limit").get("/api/search/test")
                    .header("X-Forwarded-For", "#{clientIp}")
                    .check(status().is(429)));

    {
        setUp(scn.injectOpen(atOnceUsers(USERS)))
                .protocols(httpProtocol)
                .assertions(global().failedRequests().count().is(0L));
    }
}
