package com.paximum.paxassist.ratelimiter;

import io.github.bucket4j.ConsumptionProbe;
import io.github.bucket4j.distributed.BucketProxy;
import io.github.bucket4j.distributed.proxy.RemoteBucketBuilder;
import io.github.bucket4j.redis.lettuce.cas.LettuceBasedProxyManager;
import io.lettuce.core.RedisConnectionException;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class RateLimitFilterTest {

    @SuppressWarnings("unchecked")
    private final ObjectProvider<LettuceBasedProxyManager<String>> proxyProvider = mock(ObjectProvider.class);
    @SuppressWarnings("unchecked")
    private final LettuceBasedProxyManager<String> proxyManager = mock(LettuceBasedProxyManager.class);
    @SuppressWarnings("unchecked")
    private final RemoteBucketBuilder<String> builder = mock(RemoteBucketBuilder.class);
    private final BucketProxy bucket = mock(BucketProxy.class);
    private final RateLimitPolicyProvider policyProvider = mock(RateLimitPolicyProvider.class);
    private final RateLimitKeyResolver keyResolver = mock(RateLimitKeyResolver.class);
    private final RateLimitResponseWriter responseWriter = mock(RateLimitResponseWriter.class);
    private final RateLimitProperties properties = new RateLimitProperties();
    private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();

    private RateLimitFilter filter;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private FilterChain chain;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        when(proxyProvider.getObject()).thenReturn(proxyManager);
        when(proxyManager.builder()).thenReturn(builder);
        when(builder.build(anyString(), any(Supplier.class))).thenReturn(bucket);
        when(policyProvider.resolvePolicy(anyString()))
                .thenReturn(new RateLimitPolicy("/api/booking/**", 10, 10, 60));
        when(keyResolver.resolve(any())).thenReturn("1.2.3.4");

        filter = new RateLimitFilter(
                policyProvider, keyResolver, proxyProvider, properties, responseWriter, meterRegistry);
        request = new MockHttpServletRequest();
        request.setRequestURI("/api/booking/1");
        response = new MockHttpServletResponse();
        chain = mock(FilterChain.class);
    }

    @Test
    void consumed_setsRemainingHeaderAndContinues() throws Exception {
        ConsumptionProbe probe = mock(ConsumptionProbe.class);
        when(probe.isConsumed()).thenReturn(true);
        when(probe.getRemainingTokens()).thenReturn(9L);
        when(bucket.tryConsumeAndReturnRemaining(1)).thenReturn(probe);

        filter.doFilter(request, response, chain);

        assertThat(response.getHeader("X-RateLimit-Remaining")).isEqualTo("9");
        verify(chain).doFilter(request, response);
        verifyNoInteractions(responseWriter);
    }

    @Test
    void rejected_writesRejectionWithCeiledRetryAfter_andDoesNotContinue() throws Exception {
        ConsumptionProbe probe = mock(ConsumptionProbe.class);
        when(probe.isConsumed()).thenReturn(false);
        when(probe.getNanosToWaitForRefill()).thenReturn(1_500_000_000L); // 1.5s -> ceil = 2s
        when(bucket.tryConsumeAndReturnRemaining(1)).thenReturn(probe);

        filter.doFilter(request, response, chain);

        verify(responseWriter).writeRejection(response, 2L);
        verify(chain, never()).doFilter(any(), any());
    }

    @Test
    void redisFailure_failOpenTrue_continuesAndIncrementsCounter() throws Exception {
        properties.setFailOpen(true);
        when(bucket.tryConsumeAndReturnRemaining(1)).thenThrow(new RedisConnectionException("down"));

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        verifyNoInteractions(responseWriter);
        // Counter is tagged with the policy pattern, not the raw request path.
        double count = meterRegistry.counter("ratelimit.fail_open", "endpoint", "/api/booking/**").count();
        assertThat(count).isEqualTo(1.0);
    }

    @Test
    void redisFailure_failOpenFalse_returns503AndDoesNotContinue() throws Exception {
        properties.setFailOpen(false);
        when(bucket.tryConsumeAndReturnRemaining(1)).thenThrow(new RedisConnectionException("down"));

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(503);
        verify(chain, never()).doFilter(any(), any());
    }
}
