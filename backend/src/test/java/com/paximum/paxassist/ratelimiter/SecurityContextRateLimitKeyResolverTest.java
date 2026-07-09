package com.paximum.paxassist.ratelimiter;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;


import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

class SecurityContextRateLimitKeyResolverTest {

    private SecurityContextRateLimitKeyResolver resolver;
    private HttpServletRequest request;
    private SecurityContext securityContext;

    @BeforeEach
    void setUp() {
        resolver = new SecurityContextRateLimitKeyResolver();
        request = Mockito.mock(HttpServletRequest.class);
        securityContext = Mockito.mock(SecurityContext.class);
        SecurityContextHolder.setContext(securityContext);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void shouldReturnPrincipalNameWhenAuthenticated() {
        Authentication auth = Mockito.mock(Authentication.class);
        when(auth.isAuthenticated()).thenReturn(true);
        when(auth.getName()).thenReturn("testuser");
        when(securityContext.getAuthentication()).thenReturn(auth);

        String key = resolver.resolve(request);
        assertEquals("testuser", key);
    }

    @Test
    void shouldReturnIpAddressWhenNotAuthenticated() {
        Authentication auth = Mockito.mock(Authentication.class);
        when(auth.isAuthenticated()).thenReturn(false);
        when(securityContext.getAuthentication()).thenReturn(auth);
        when(request.getRemoteAddr()).thenReturn("192.168.1.1");

        String key = resolver.resolve(request);
        assertEquals("192.168.1.1", key);
    }

    @Test
    void shouldReturnIpAddressWhenAnonymousAuthentication() {
        AnonymousAuthenticationToken anonymousAuth = new AnonymousAuthenticationToken(
                "key", "anonymousUser", java.util.List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_ANONYMOUS"))
        );
        when(securityContext.getAuthentication()).thenReturn(anonymousAuth);
        when(request.getRemoteAddr()).thenReturn("10.0.0.1");

        String key = resolver.resolve(request);
        assertEquals("10.0.0.1", key);
    }

    @Test
    void shouldReturnIpAddressWhenAuthenticationIsNull() {
        when(securityContext.getAuthentication()).thenReturn(null);
        when(request.getRemoteAddr()).thenReturn("127.0.0.1");

        String key = resolver.resolve(request);
        assertEquals("127.0.0.1", key);
    }
}
