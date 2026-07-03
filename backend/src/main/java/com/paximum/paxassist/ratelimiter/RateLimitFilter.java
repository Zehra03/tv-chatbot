package com.paximum.paxassist.ratelimiter;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.ConsumptionProbe;
import io.github.bucket4j.redis.lettuce.cas.LettuceBasedProxyManager;
import io.lettuce.core.RedisException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;

/**
 * Per-request rate limiting filter backed by Bucket4j over Redis.
 *
 * <p>The Bucket4j proxy manager is obtained lazily on first use so that a Redis
 * outage at application startup does not prevent the app from booting; runtime
 * Redis failures are handled by the fail-open/fail-closed branch below.
 */
public class RateLimitFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RateLimitFilter.class);

    private final RateLimitPolicyProvider policyProvider;
    private final RateLimitKeyResolver keyResolver;
    private final ObjectProvider<LettuceBasedProxyManager<String>> proxyManagerProvider;
    private final RateLimitProperties properties;
    private final RateLimitResponseWriter responseWriter;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    public RateLimitFilter(RateLimitPolicyProvider policyProvider,
                           RateLimitKeyResolver keyResolver,
                           ObjectProvider<LettuceBasedProxyManager<String>> proxyManagerProvider,
                           RateLimitProperties properties,
                           RateLimitResponseWriter responseWriter) {
        this.policyProvider = policyProvider;
        this.keyResolver = keyResolver;
        this.proxyManagerProvider = proxyManagerProvider;
        this.properties = properties;
        this.responseWriter = responseWriter;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String path = request.getRequestURI();

        // 1. Excluded paths bypass rate limiting entirely.
        for (String excluded : properties.getExcludedPaths()) {
            if (pathMatcher.match(excluded, path)) {
                chain.doFilter(request, response);
                return;
            }
        }

        // 2. Resolve the policy for this path.
        RateLimitPolicy policy = policyProvider.resolvePolicy(path);

        // 3. Build the bucket key: resolved path pattern + ":" + per-caller key.
        String bucketKey = policy.path() + ":" + keyResolver.resolve(request);

        // 4-5. Get/create the bucket and try to consume a token, guarded against Redis failures.
        ConsumptionProbe probe;
        try {
            Bucket bucket = proxyManagerProvider.getObject()
                    .builder()
                    .build(bucketKey, () -> toBucketConfiguration(policy));
            probe = bucket.tryConsumeAndReturnRemaining(1);
        } catch (RedisException ex) {
            // Only Redis/Lettuce infrastructure failures are treated as outages here
            // (RedisConnectionException, RedisCommandTimeoutException, etc.). Any other
            // exception is a genuine bug and must propagate, not be masked as fail-open.
            if (properties.isFailOpen()) {
                log.warn("Rate limiter unavailable for path {} — failing open: {}", path, ex.toString());
                chain.doFilter(request, response);
            } else {
                response.setStatus(HttpStatus.SERVICE_UNAVAILABLE.value());
            }
            return;
        }

        // 6. Consumed: expose remaining tokens and continue the chain.
        if (probe.isConsumed()) {
            response.addHeader("X-RateLimit-Remaining", String.valueOf(probe.getRemainingTokens()));
            chain.doFilter(request, response);
            return;
        }

        // 7. Rejected: write the response here and do NOT continue the chain.
        responseWriter.writeLimitExceeded(request, response, probe);
    }

    private BucketConfiguration toBucketConfiguration(RateLimitPolicy policy) {
        Bandwidth limit = Bandwidth.builder()
                .capacity(policy.capacity())
                .refillGreedy(policy.refillTokens(), Duration.ofSeconds(policy.refillPeriodSeconds()))
                .build();
        return BucketConfiguration.builder()
                .addLimit(limit)
                .build();
    }
}
