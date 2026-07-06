package com.paximum.paxassist.ratelimiter;

import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import io.github.bucket4j.redis.lettuce.cas.LettuceBasedProxyManager;
import io.lettuce.core.RedisException;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;
import org.springframework.beans.factory.ObjectProvider;

import java.io.IOException;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class RateLimitFilterTest {

    private RateLimitFilter filter;
    private RateLimitPolicyProvider policyProvider;
    private RateLimitKeyResolver keyResolver;
    private ObjectProvider<LettuceBasedProxyManager<String>> proxyManagerProvider;
    private LettuceBasedProxyManager<String> proxyManager;
    private RateLimitProperties properties;
    private RateLimitResponseWriter responseWriter;
    private MeterRegistry meterRegistry;
    private Counter failOpenCounter;

    private HttpServletRequest request;
    private HttpServletResponse response;
    private FilterChain filterChain;
    private io.github.bucket4j.distributed.BucketProxy bucket;
    private ConsumptionProbe probe;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        policyProvider = mock(RateLimitPolicyProvider.class);
        keyResolver = mock(RateLimitKeyResolver.class);
        proxyManagerProvider = mock(ObjectProvider.class);
        proxyManager = mock(LettuceBasedProxyManager.class, Answers.RETURNS_DEEP_STUBS);
        properties = new RateLimitProperties();
        responseWriter = mock(RateLimitResponseWriter.class);
        meterRegistry = mock(MeterRegistry.class);
        failOpenCounter = mock(Counter.class);

        when(meterRegistry.counter(anyString(), anyString(), anyString())).thenReturn(failOpenCounter);
        when(proxyManagerProvider.getObject()).thenReturn(proxyManager);

        request = mock(HttpServletRequest.class);
        response = mock(HttpServletResponse.class);
        filterChain = mock(FilterChain.class);
        bucket = mock(io.github.bucket4j.distributed.BucketProxy.class);
        probe = mock(ConsumptionProbe.class);

        when(proxyManager.builder().build(anyString(), (java.util.function.Supplier<io.github.bucket4j.BucketConfiguration>) any())).thenReturn(bucket);
        when(bucket.tryConsumeAndReturnRemaining(1)).thenReturn(probe);

        filter = new RateLimitFilter(
                policyProvider,
                keyResolver,
                proxyManagerProvider,
                properties,
                responseWriter,
                meterRegistry
        );
    }

    @Test
    void shouldBypassExcludedPaths() throws ServletException, IOException {
        properties.setExcludedPaths(List.of("/health", "/metrics/**"));
        when(request.getRequestURI()).thenReturn("/health");

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        verifyNoInteractions(policyProvider, keyResolver, proxyManagerProvider);
    }

    @Test
    void shouldProceedWhenTokenConsumed() throws ServletException, IOException {
        when(request.getRequestURI()).thenReturn("/api/test");
        when(policyProvider.resolvePolicy("/api/test")).thenReturn(new RateLimitPolicy("/api/**", 10, 10, 60));
        when(keyResolver.resolve(request)).thenReturn("user1");
        
        when(probe.isConsumed()).thenReturn(true);
        when(probe.getRemainingTokens()).thenReturn(9L);

        filter.doFilterInternal(request, response, filterChain);

        verify(response).addHeader("X-RateLimit-Remaining", "9");
        verify(filterChain).doFilter(request, response);
        verifyNoInteractions(responseWriter);
    }

    @Test
    void shouldRejectWhenTokenNotConsumed() throws ServletException, IOException {
        when(request.getRequestURI()).thenReturn("/api/test");
        when(policyProvider.resolvePolicy("/api/test")).thenReturn(new RateLimitPolicy("/api/**", 10, 10, 60));
        when(keyResolver.resolve(request)).thenReturn("user1");
        
        when(probe.isConsumed()).thenReturn(false);
        when(probe.getNanosToWaitForRefill()).thenReturn(2_500_000_000L); // 2.5 seconds

        filter.doFilterInternal(request, response, filterChain);

        verify(responseWriter).writeRejection(response, 3L); // ceil(2.5)
        verifyNoInteractions(filterChain);
    }

    @Test
    void shouldFailOpenOnRedisExceptionIfConfigured() throws ServletException, IOException {
        properties.setFailOpen(true);
        when(request.getRequestURI()).thenReturn("/api/test");
        when(policyProvider.resolvePolicy("/api/test")).thenReturn(new RateLimitPolicy("/api/**", 10, 10, 60));
        when(keyResolver.resolve(request)).thenReturn("user1");
        
        when(proxyManager.builder().build(anyString(), (java.util.function.Supplier<io.github.bucket4j.BucketConfiguration>) any())).thenThrow(new RedisException("Redis is down"));

        filter.doFilterInternal(request, response, filterChain);

        verify(meterRegistry).counter("ratelimit.fail_open", "endpoint", "/api/**");
        verify(failOpenCounter).increment();
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void shouldReturnServiceUnavailableOnRedisExceptionIfFailOpenIsFalse() throws ServletException, IOException {
        properties.setFailOpen(false);
        when(request.getRequestURI()).thenReturn("/api/test");
        when(policyProvider.resolvePolicy("/api/test")).thenReturn(new RateLimitPolicy("/api/**", 10, 10, 60));
        when(keyResolver.resolve(request)).thenReturn("user1");
        
        when(proxyManager.builder().build(anyString(), (java.util.function.Supplier<io.github.bucket4j.BucketConfiguration>) any())).thenThrow(new RedisException("Redis is down"));

        filter.doFilterInternal(request, response, filterChain);

        verify(response).setStatus(503);
        verifyNoInteractions(filterChain);
    }
}
