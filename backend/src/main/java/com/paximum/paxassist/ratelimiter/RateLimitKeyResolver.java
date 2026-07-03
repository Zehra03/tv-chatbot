package com.paximum.paxassist.ratelimiter;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Resolves the per-caller portion of a rate limit bucket key from a request
 * (e.g. a user id, an API key, or the client IP).
 *
 * <p>The full bucket key is the resolved path pattern joined to this key with a
 * colon, e.g. {@code "/api/search/**:user123"}. Implementations are added in a
 * later card — this card only defines the contract.
 */
public interface RateLimitKeyResolver {

    String resolve(HttpServletRequest request);
}
