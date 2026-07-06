package com.paximum.paxassist.ratelimiter;

import java.util.Comparator;

import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;

/**
 * Default {@link RateLimitPolicyProvider} backed by {@link RateLimitProperties}.
 * This is the only class allowed to read the rate limiting configuration bean.
 *
 * <p>Matching uses {@link AntPathMatcher}: among all policies whose pattern matches
 * the request path, the most specific one wins (per Ant pattern specificity). When
 * nothing matches, the configured default policy is returned.
 */
@Component
public class DefaultRateLimitPolicyProvider implements RateLimitPolicyProvider {

    private final RateLimitProperties properties;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    public DefaultRateLimitPolicyProvider(RateLimitProperties properties) {
        this.properties = properties;
    }

    @Override
    public RateLimitPolicy resolvePolicy(String path) {
        Comparator<String> specificityComparator = pathMatcher.getPatternComparator(path);
        return properties.getPolicies().stream()
                .filter(policy -> pathMatcher.match(policy.path(), path))
                .min((a, b) -> specificityComparator.compare(a.path(), b.path()))
                .orElseGet(properties::getDefaultPolicy);
    }
}
