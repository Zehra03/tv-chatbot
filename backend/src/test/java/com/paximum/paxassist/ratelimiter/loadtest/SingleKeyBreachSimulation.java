package com.paximum.paxassist.ratelimiter.loadtest;

import io.gatling.javaapi.core.ScenarioBuilder;
import io.gatling.javaapi.core.Simulation;
import io.gatling.javaapi.http.HttpProtocolBuilder;

import static io.gatling.javaapi.core.CoreDsl.*;
import static io.gatling.javaapi.http.HttpDsl.*;

/**
 * Scenario 1 — single-key limit breach.
 *
 * <p>A single client (fixed X-Forwarded-For) hits {@code /api/search/test}. The first
 * {@code CAPACITY} requests must return 200; every request beyond capacity must return
 * 429. Uses the {@code loadtest} profile's /api/search/** policy (capacity 10).
 */
public class SingleKeyBreachSimulation extends Simulation {

    private static final String BASE_URL = System.getProperty("baseUrl", "http://localhost:8080");
    private static final int CAPACITY = Integer.getInteger("capacity", 10);
    private static final int OVER_LIMIT = 5;

    private final HttpProtocolBuilder httpProtocol = http
            .baseUrl(BASE_URL)
            .acceptHeader("application/json")
            .header("X-Forwarded-For", "10.0.0.1");

    private final ScenarioBuilder scn = scenario("Single-key limit breach")
            .repeat(CAPACITY).on(
                    exec(http("within limit").get("/api/search/test").check(status().is(200))))
            .repeat(OVER_LIMIT).on(
                    exec(http("over limit").get("/api/search/test").check(status().is(429))));

    {
        setUp(scn.injectOpen(atOnceUsers(1)))
                .protocols(httpProtocol)
                .assertions(global().failedRequests().count().is(0L));
    }
}
