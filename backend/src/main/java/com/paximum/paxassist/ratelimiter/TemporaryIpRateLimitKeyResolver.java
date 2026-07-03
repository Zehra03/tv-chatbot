package com.paximum.paxassist.ratelimiter;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

/**
 * Temporary key resolver that keys rate limit buckets by client IP address.
 */
@Component
public class TemporaryIpRateLimitKeyResolver implements RateLimitKeyResolver {

    // TODO: Replace with SecurityContext-based resolver once Spring Security auth is merged.
    @Override
    public String resolve(HttpServletRequest request) {
        return request.getRemoteAddr();
    }
}
