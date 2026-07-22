package com.paximum.paxassist.common.log;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import com.paximum.paxassist.auth.domain.Role;
import com.paximum.paxassist.auth.domain.User;
import com.paximum.paxassist.auth.security.UserPrincipal;

import jakarta.servlet.FilterChain;

/**
 * Correlation is what turns a flat log stream into something you can follow, so the fields must be
 * present during the request and gone after it.
 */
class RequestCorrelationFilterTest {

    private final RequestCorrelationFilter filter = new RequestCorrelationFilter();

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        MDC.clear();
    }

    @Test
    void guestRequest_isCorrelatedByItsGuestId() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/reservations");
        request.addHeader("X-Guest-Id", "guest-abc");
        MockHttpServletResponse response = new MockHttpServletResponse();

        CapturingChain chain = new CapturingChain();
        filter.doFilter(request, response, chain);

        assertThat(chain.guestId).isEqualTo("guest-abc");
        assertThat(chain.userId).isNull();
        assertThat(chain.requestId).isNotBlank();
        // Echoed back so a user reporting a problem can quote the id from their network tab.
        assertThat(response.getHeader("X-Request-Id")).isEqualTo(chain.requestId);
    }

    @Test
    void authenticatedRequest_isCorrelatedByUserIdAndNotAsAGuest() throws Exception {
        // A logged-in caller is a user, not a guest — even if a stale guest id is still in their
        // browser and rides along on the request (which is exactly what the frontend does).
        authenticateAs(42L);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/reservations");
        request.addHeader("X-Guest-Id", "stale-guest-id");

        CapturingChain chain = new CapturingChain();
        filter.doFilter(request, new MockHttpServletResponse(), chain);

        assertThat(chain.userId).isEqualTo("42");
        assertThat(chain.guestId).isNull();
    }

    @Test
    void inboundRequestId_isReusedSoATraceJoinsUpAcrossHops() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/hotels/search");
        request.addHeader("X-Request-Id", "frontend-trace-1");

        CapturingChain chain = new CapturingChain();
        filter.doFilter(request, new MockHttpServletResponse(), chain);

        assertThat(chain.requestId).isEqualTo("frontend-trace-1");
    }

    @Test
    void overlongInboundRequestId_isReplacedRatherThanUsed() throws Exception {
        // The header is attacker-controlled; without a bound, a caller could pad every log line.
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/hotels/search");
        request.addHeader("X-Request-Id", "x".repeat(65));

        CapturingChain chain = new CapturingChain();
        filter.doFilter(request, new MockHttpServletResponse(), chain);

        assertThat(chain.requestId).isNotEqualTo("x".repeat(65));
        assertThat(chain.requestId).hasSizeLessThanOrEqualTo(64);
    }

    @Test
    void fieldsAreClearedAfterTheRequest_soTheyDoNotLeakOntoTheNextOne() throws Exception {
        // Tomcat reuses worker threads: a leak would make the next visitor's lines carry this
        // visitor's identity — evidence of something they never did.
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/chat");
        request.addHeader("X-Guest-Id", "guest-abc");

        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

        assertThat(MDC.getCopyOfContextMap()).isNullOrEmpty();
    }

    private void authenticateAs(Long userId) {
        User user = User.builder()
                .email("corr@example.com")
                .passwordHash("x")
                .displayName("Corr")
                .role(Role.USER)
                .build();
        user.setId(userId);
        UserPrincipal principal = new UserPrincipal(user);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }

    /** Reads the MDC from inside the chain — the only place the fields are supposed to exist. */
    private static final class CapturingChain implements FilterChain {
        private String requestId;
        private String userId;
        private String guestId;

        @Override
        public void doFilter(jakarta.servlet.ServletRequest request, jakarta.servlet.ServletResponse response) {
            requestId = MDC.get(RequestCorrelationFilter.REQUEST_ID);
            userId = MDC.get(RequestCorrelationFilter.USER_ID);
            guestId = MDC.get(RequestCorrelationFilter.GUEST_ID);
        }
    }
}
