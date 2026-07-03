package com.paximum.paxassist.ratelimiter;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

class TemporaryIpRateLimitKeyResolverTest {

    private final TemporaryIpRateLimitKeyResolver resolver = new TemporaryIpRateLimitKeyResolver();

    @Test
    void usesRemoteAddr_whenNoForwardedForHeader() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("203.0.113.7");

        assertThat(resolver.resolve(request)).isEqualTo("203.0.113.7");
    }

    @Test
    void usesFirstForwardedForAddress_whenHeaderPresent() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("10.0.0.1");
        request.addHeader("X-Forwarded-For", "198.51.100.23, 10.0.0.1, 10.0.0.2");

        assertThat(resolver.resolve(request)).isEqualTo("198.51.100.23");
    }

    @Test
    void fallsBackToRemoteAddr_whenForwardedForBlank() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("10.0.0.9");
        request.addHeader("X-Forwarded-For", "   ");

        assertThat(resolver.resolve(request)).isEqualTo("10.0.0.9");
    }
}
