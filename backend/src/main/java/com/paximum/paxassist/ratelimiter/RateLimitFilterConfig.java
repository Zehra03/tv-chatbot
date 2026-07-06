package com.paximum.paxassist.ratelimiter;

import io.github.bucket4j.redis.lettuce.cas.LettuceBasedProxyManager;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Exposes {@link RateLimitFilter} as a bean so the security layer can place it in the
 * chain. Placement happens in {@code SecurityConfig} via
 * {@code http.addFilterAfter(rateLimitFilter, JwtAuthenticationFilter.class)} — the
 * limiter runs after authentication so it can key buckets by the authenticated principal.
 *
 * <p>Because a {@code Filter} bean would otherwise be auto-registered by Spring Boot in
 * the raw servlet chain (running it a second time, before Spring Security), the
 * {@link FilterRegistrationBean} below disables that automatic registration.
 */
@Configuration
public class RateLimitFilterConfig {

    @Bean
    public RateLimitFilter rateLimitFilter(
            RateLimitPolicyProvider policyProvider,
            RateLimitKeyResolver keyResolver,
            ObjectProvider<LettuceBasedProxyManager<String>> proxyManagerProvider,
            RateLimitProperties properties,
            RateLimitResponseWriter responseWriter,
            MeterRegistry meterRegistry) {

        return new RateLimitFilter(
                policyProvider, keyResolver, proxyManagerProvider, properties, responseWriter, meterRegistry);
    }

    @Bean
    public FilterRegistrationBean<RateLimitFilter> rateLimitFilterServletRegistration(
            RateLimitFilter rateLimitFilter) {
        // Prevent Spring Boot from auto-registering the filter bean in the servlet
        // container; it is placed explicitly inside the Spring Security filter chain.
        FilterRegistrationBean<RateLimitFilter> registration = new FilterRegistrationBean<>(rateLimitFilter);
        registration.setEnabled(false);
        return registration;
    }
}
