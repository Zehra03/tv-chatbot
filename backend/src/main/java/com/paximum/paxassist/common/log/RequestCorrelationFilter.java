package com.paximum.paxassist.common.log;

import java.io.IOException;
import java.util.UUID;

import org.slf4j.MDC;
import org.springframework.lang.NonNull;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import com.paximum.paxassist.auth.security.UserPrincipal;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Stamps every log line produced while handling a request with the identity of that request:
 * a generated {@code requestId}, plus whoever is making it.
 *
 * <p><b>Why this is the piece that makes JSON logs worth having.</b> Without it the log stream is a
 * flat list of events with no way to join them: when a guest says "my booking failed", there is no
 * key to filter their chat turn, their search and their confirm attempt into one story. With it,
 * one {@code requestId} filter returns exactly the lines belonging to one request, and one
 * {@code guestId}/{@code userId} filter returns everything that visitor did.
 *
 * <p><b>Fields</b> (MDC keys → top-level attributes in the ECS JSON output):
 * <ul>
 *   <li>{@code requestId} — from an inbound {@code X-Request-Id} if the caller sent one (so a
 *       frontend or proxy trace joins up with ours), otherwise generated here;</li>
 *   <li>{@code userId} — the authenticated principal's id, never taken from the request body;</li>
 *   <li>{@code guestId} — the opaque {@code X-Guest-Id} of an anonymous visitor. Recorded only when
 *       there is no authenticated user, mirroring how ownership is resolved everywhere else: a
 *       logged-in caller is a user, not a guest.</li>
 * </ul>
 *
 * <p>The {@code requestId} is echoed back as {@code X-Request-Id} so a user reporting a problem can
 * quote the id straight from their browser's network tab.
 *
 * <p><b>Placement:</b> registered in the security chain after {@code JwtAuthenticationFilter} (see
 * {@code SecurityConfig}) — the principal only exists once that filter has run, so this filter must
 * NOT be a plain {@code @Component}: Spring Boot would then also auto-register it in the raw servlet
 * chain, where it runs first, before authentication, and would record every caller as anonymous.
 * {@code RequestCorrelationFilterConfig} disables that auto-registration, exactly as the rate
 * limiter does.
 *
 * <p>A 401/403 still gets its fields: the authorization decision is made further down the chain, so
 * the entry point runs while unwinding back through this filter and the MDC is still populated —
 * verified against real output. What is genuinely uncovered is anything rejected <em>before</em> the
 * JWT filter (a CORS preflight rejection), which never reaches application code anyway.
 *
 * <p><b>PII:</b> deliberately no e-mail, name or IP. The ids here are opaque handles that can be
 * joined to a person only through the database, which is what a log needs and no more.
 */
public class RequestCorrelationFilter extends OncePerRequestFilter {

    static final String REQUEST_ID = "requestId";
    static final String USER_ID = "userId";
    static final String GUEST_ID = "guestId";

    private static final String REQUEST_ID_HEADER = "X-Request-Id";
    private static final String GUEST_ID_HEADER = "X-Guest-Id";

    /** Bound to the same 64 chars as the guest_token column; longer values are ignored, not stored. */
    private static final int MAX_ID_LENGTH = 64;

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain) throws ServletException, IOException {

        String requestId = inboundRequestId(request);
        MDC.put(REQUEST_ID, requestId);
        response.setHeader(REQUEST_ID_HEADER, requestId);

        Long userId = authenticatedUserId();
        if (userId != null) {
            MDC.put(USER_ID, String.valueOf(userId));
        } else {
            String guestId = trimmedHeader(request, GUEST_ID_HEADER);
            if (guestId != null) {
                MDC.put(GUEST_ID, guestId);
            }
        }

        try {
            filterChain.doFilter(request, response);
        } finally {
            // Tomcat reuses worker threads. A leaked MDC would stamp this request's identity onto
            // the next, unrelated request handled by the same thread — which is worse than having no
            // correlation at all, because it reads as evidence that a user did something they didn't.
            MDC.remove(REQUEST_ID);
            MDC.remove(USER_ID);
            MDC.remove(GUEST_ID);
        }
    }

    /**
     * An inbound id is trusted only as a correlation hint — it is never used for authorization, so a
     * forged one can at most muddle a log query. Over-length values are dropped rather than
     * truncated so a caller cannot pad the log line at will.
     */
    private String inboundRequestId(HttpServletRequest request) {
        String inbound = trimmedHeader(request, REQUEST_ID_HEADER);
        return inbound != null ? inbound : UUID.randomUUID().toString();
    }

    private Long authenticatedUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof UserPrincipal principal)) {
            return null;
        }
        return principal.getId();
    }

    private String trimmedHeader(HttpServletRequest request, String name) {
        String value = request.getHeader(name);
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty() || trimmed.length() > MAX_ID_LENGTH) {
            return null;
        }
        return trimmed;
    }
}
