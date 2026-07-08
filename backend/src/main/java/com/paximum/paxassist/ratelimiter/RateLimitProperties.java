package com.paximum.paxassist.ratelimiter;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * Type-safe binding for the {@code ratelimit.*} configuration tree.
 *
 * <p>This bean is the single source of rate limiting configuration. It must not be
 * consumed directly by request-handling code; access policies through
 * {@link RateLimitPolicyProvider} instead.
 */
@ConfigurationProperties(prefix = "ratelimit")
public class RateLimitProperties {

    /** Ordered list of specific rules; the most specific matching path wins. */
    private List<RateLimitPolicy> policies = new ArrayList<>();

    /** Fallback policy (path "/**") applied when no entry in {@link #policies} matches. */
    private RateLimitPolicy defaultPolicy;

    /** Ant path patterns that bypass rate limiting entirely. Structure only for now. */
    private List<String> excludedPaths = new ArrayList<>();

    /** When true, a rate limiter failure (e.g. Redis down) lets the request through. */
    private boolean failOpen = true;

    public List<RateLimitPolicy> getPolicies() {
        return policies;
    }

    public void setPolicies(List<RateLimitPolicy> policies) {
        this.policies = policies;
    }

    public RateLimitPolicy getDefaultPolicy() {
        return defaultPolicy;
    }

    public void setDefaultPolicy(RateLimitPolicy defaultPolicy) {
        this.defaultPolicy = defaultPolicy;
    }

    public List<String> getExcludedPaths() {
        return excludedPaths;
    }

    public void setExcludedPaths(List<String> excludedPaths) {
        this.excludedPaths = excludedPaths;
    }

    public boolean isFailOpen() {
        return failOpen;
    }

    public void setFailOpen(boolean failOpen) {
        this.failOpen = failOpen;
    }
}
