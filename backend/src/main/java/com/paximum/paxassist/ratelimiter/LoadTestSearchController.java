package com.paximum.paxassist.ratelimiter;

import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Trivial endpoint used only by the rate limiter load tests. Active exclusively
 * under the {@code loadtest} profile, so it is never exposed in production.
 * Matched by the {@code /api/search/**} rate limit policy.
 */
@RestController
@Profile("loadtest")
public class LoadTestSearchController {

    @GetMapping("/api/search/test")
    public String search() {
        return "ok";
    }
}
