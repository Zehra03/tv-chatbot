package com.paximum.paxassist.ratelimiter;

import io.github.bucket4j.redis.lettuce.cas.LettuceBasedProxyManager;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

/**
 * Registers {@link RateLimitFilter} in the servlet filter chain.
 *
 * <p>The filter is built inside the registration bean (rather than exposed as its
 * own {@code Filter} bean) to avoid Spring Boot auto-registering it a second time.
 */
@Configuration
public class RateLimitFilterConfig {

    @Bean
    public FilterRegistrationBean<RateLimitFilter> rateLimitFilterRegistration(
            RateLimitPolicyProvider policyProvider,
            RateLimitKeyResolver keyResolver,
            ObjectProvider<LettuceBasedProxyManager<String>> proxyManagerProvider,
            RateLimitProperties properties,
            RateLimitResponseWriter responseWriter) {

        RateLimitFilter filter = new RateLimitFilter(
                policyProvider, keyResolver, proxyManagerProvider, properties, responseWriter);

        FilterRegistrationBean<RateLimitFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setOrder(Ordered.LOWEST_PRECEDENCE - 100);
        registration.addUrlPatterns("/*");
        // TODO: Once Spring Security is merged, remove this registration and instead
        // wire via SecurityFilterChain.addFilterAfter(rateLimitFilter, <AuthFilterClass>.class)
        return registration;
    }
}
