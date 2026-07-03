package com.paximum.paxassist.ratelimiter;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

/**
 * Temporary key resolver that keys rate limit buckets by client IP address.
 *
 * <p>When the request carries an {@code X-Forwarded-For} header (i.e. it arrived
 * through a proxy/load balancer, or a load test supplies one), the first address in
 * that list is used as the originating client; otherwise the socket remote address
 * is used.
 */
@Component
public class TemporaryIpRateLimitKeyResolver implements RateLimitKeyResolver {

    // TODO: Replace with SecurityContext-based resolver once Spring Security auth is merged.
    @Override
    public String resolve(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            int comma = forwardedFor.indexOf(',');
            String first = (comma == -1) ? forwardedFor : forwardedFor.substring(0, comma);
            return first.trim();
        }
        return request.getRemoteAddr();
    }
}
