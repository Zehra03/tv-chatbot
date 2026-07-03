package com.paximum.paxassist.ratelimiter.loadtest;

import io.gatling.javaapi.core.ScenarioBuilder;
import io.gatling.javaapi.core.Simulation;
import io.gatling.javaapi.http.HttpProtocolBuilder;

import static io.gatling.javaapi.core.CoreDsl.*;
import static io.gatling.javaapi.http.HttpDsl.*;

/**
 * Scenario 4 — excluded paths.
 *
 * <p>High-volume requests to an excluded path ({@code /actuator/health}, listed in the
 * loadtest profile's {@code ratelimit.excluded-paths}). No request may ever be rejected
 * with 429 regardless of volume — the check asserts 200 on every call.
 */
public class ExcludedPathSimulation extends Simulation {

    private static final String BASE_URL = System.getProperty("baseUrl", "http://localhost:8080");
    private static final int REQUESTS_PER_USER = Integer.getInteger("requestsPerUser", 50);
    private static final int USERS = Integer.getInteger("users", 5);

    private final HttpProtocolBuilder httpProtocol = http
            .baseUrl(BASE_URL)
            .acceptHeader("application/json")
            .header("X-Forwarded-For", "10.3.0.1");

    private final ScenarioBuilder scn = scenario("Excluded path is never rate limited")
            .repeat(REQUESTS_PER_USER).on(
                    exec(http("excluded path").get("/actuator/health")
                            .check(status().is(200))
                            .check(status().not(429))));

    {
        setUp(scn.injectOpen(atOnceUsers(USERS)))
                .protocols(httpProtocol)
                .assertions(global().failedRequests().count().is(0L));
    }
}
