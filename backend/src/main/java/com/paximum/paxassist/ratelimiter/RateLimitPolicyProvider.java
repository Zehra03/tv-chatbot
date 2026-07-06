package com.paximum.paxassist.ratelimiter;

/**
 * Resolves the {@link RateLimitPolicy} that applies to a request path, hiding all
 * access to the underlying {@link RateLimitProperties} configuration bean.
 */
public interface RateLimitPolicyProvider {

    /**
     * Returns the most specific policy whose pattern matches {@code path}, or the
     * configured default/fallback policy when none matches.
     *
     * @param path the request path (e.g. "/api/booking/123").
     * @return the applicable policy; never {@code null} when a default policy is configured.
     */
    RateLimitPolicy resolvePolicy(String path);
}
