package com.paximum.paxassist.common.log;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Exposes {@link RequestCorrelationFilter} as a bean so the security layer can place it in the
 * chain. Placement happens in {@code SecurityConfig} via
 * {@code http.addFilterAfter(correlationFilter, JwtAuthenticationFilter.class)} — correlation runs
 * after authentication so it can record the authenticated principal's id.
 *
 * <p>Because a {@code Filter} bean would otherwise be auto-registered by Spring Boot in the raw
 * servlet chain, the {@link FilterRegistrationBean} below disables that automatic registration —
 * mirroring {@code RateLimitFilterConfig}. This is not just tidiness: the servlet-chain copy runs
 * <em>before</em> Spring Security, and {@code OncePerRequestFilter} lets only the first invocation
 * through, so leaving it enabled would silently record every request as anonymous.
 */
@Configuration
public class RequestCorrelationFilterConfig {

    @Bean
    public RequestCorrelationFilter requestCorrelationFilter() {
        return new RequestCorrelationFilter();
    }

    @Bean
    public FilterRegistrationBean<RequestCorrelationFilter> requestCorrelationFilterServletRegistration(
            RequestCorrelationFilter requestCorrelationFilter) {
        FilterRegistrationBean<RequestCorrelationFilter> registration =
                new FilterRegistrationBean<>(requestCorrelationFilter);
        registration.setEnabled(false);
        return registration;
    }
}
