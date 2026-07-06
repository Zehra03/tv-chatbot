package com.paximum.paxassist.ratelimiter;

/**
 * A single rate limiting rule bound from configuration.
 *
 * @param path                Ant-style path pattern this policy applies to (e.g. "/api/booking/**").
 * @param capacity            maximum number of tokens the bucket can hold.
 * @param refillTokens        number of tokens added each refill period.
 * @param refillPeriodSeconds length of the refill period in seconds.
 */
public record RateLimitPolicy(
        String path,
        long capacity,
        long refillTokens,
        long refillPeriodSeconds
) {
}
