package com.paximum.paxassist.ratelimiter;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * Keys rate limit buckets by the authenticated principal, so limits are enforced
 * per user rather than per source IP. This filter runs after {@code JwtAuthenticationFilter}
 * in the security chain, so an authenticated request already has its principal set.
 *
 * <p>Requests without an authenticated principal (unauthenticated, or the anonymous
 * token Spring Security assigns to permit-all endpoints such as {@code /auth/login})
 * fall back to the client socket address so those callers are still throttled.
 */
@Component
public class SecurityContextRateLimitKeyResolver implements RateLimitKeyResolver {

    @Override
    public String resolve(HttpServletRequest request) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null
                && authentication.isAuthenticated()
                && !(authentication instanceof AnonymousAuthenticationToken)) {
            return authentication.getName();
        }
        return request.getRemoteAddr();
    }
}
