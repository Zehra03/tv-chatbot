package com.paximum.paxassist.ratelimiter.loadtest;

import io.gatling.javaapi.core.ScenarioBuilder;
import io.gatling.javaapi.core.Simulation;
import io.gatling.javaapi.http.HttpProtocolBuilder;

import static io.gatling.javaapi.core.CoreDsl.*;
import static io.gatling.javaapi.http.HttpDsl.*;

/**
 * Scenario 3 — refill after Retry-After.
 *
 * <p>Exhaust the bucket, read {@code retryAfterSeconds} from the 429 body, pause
 * exactly that long, then send one more request which must return 200 (a token has
 * refilled). Single user so the timing is deterministic.
 */
public class RefillSimulation extends Simulation {

    private static final String BASE_URL = System.getProperty("baseUrl", "http://localhost:8080");
    private static final int CAPACITY = Integer.getInteger("capacity", 10);

    private final HttpProtocolBuilder httpProtocol = http
            .baseUrl(BASE_URL)
            .acceptHeader("application/json")
            .header("X-Forwarded-For", "10.2.0.1");

    private final ScenarioBuilder scn = scenario("Refill after Retry-After")
            .repeat(CAPACITY).on(
                    exec(http("exhaust").get("/api/search/test").check(status().is(200))))
            .exec(http("rejected").get("/api/search/test")
                    .check(status().is(429))
                    .check(jsonPath("$.retryAfterSeconds").saveAs("retryAfter")))
            // Pause exactly the server-advertised Retry-After (seconds) before retrying.
            .pause("#{retryAfter}")
            .exec(http("after refill").get("/api/search/test").check(status().is(200)));

    {
        setUp(scn.injectOpen(atOnceUsers(1)))
                .protocols(httpProtocol)
                .assertions(global().failedRequests().count().is(0L));
    }
}
