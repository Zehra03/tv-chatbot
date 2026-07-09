package com.paximum.paxassist.ratelimiter;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DefaultRateLimitPolicyProviderTest {

    private DefaultRateLimitPolicyProvider policyProvider;
    private RateLimitProperties properties;

    @BeforeEach
    void setUp() {
        properties = new RateLimitProperties();
        
        RateLimitPolicy defaultPolicy = new RateLimitPolicy("/**", 100, 100, 60);
        RateLimitPolicy apiPolicy = new RateLimitPolicy("/api/**", 50, 50, 60);
        RateLimitPolicy bookingPolicy = new RateLimitPolicy("/api/booking/**", 10, 10, 60);
        
        properties.setDefaultPolicy(defaultPolicy);
        properties.setPolicies(List.of(apiPolicy, bookingPolicy));

        policyProvider = new DefaultRateLimitPolicyProvider(properties);
    }

    @Test
    void shouldReturnMostSpecificPolicy() {
        RateLimitPolicy policy = policyProvider.resolvePolicy("/api/booking/123");
        assertEquals("/api/booking/**", policy.path());
        assertEquals(10, policy.capacity());
    }

    @Test
    void shouldReturnLessSpecificPolicyWhenMostSpecificDoesNotMatch() {
        RateLimitPolicy policy = policyProvider.resolvePolicy("/api/users/123");
        assertEquals("/api/**", policy.path());
        assertEquals(50, policy.capacity());
    }

    @Test
    void shouldReturnDefaultPolicyWhenNoOtherMatches() {
        RateLimitPolicy policy = policyProvider.resolvePolicy("/public/info");
        assertEquals("/**", policy.path());
        assertEquals(100, policy.capacity());
    }
}
