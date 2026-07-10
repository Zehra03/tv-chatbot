package com.paximum.paxassist.guard.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration for the stateful guard rules that need per-user state (unlike the stateless
 * regex checks in {@code GuardRuleService}, which need no config).
 *
 * @param outOfScope tuning for the consecutive out-of-scope (OTHER intent) abuse block.
 */
@ConfigurationProperties(prefix = "app.guard")
public record GuardProperties(OutOfScope outOfScope) {

    /**
     * Temporarily blocks a user who sends too many <b>consecutive</b> out-of-scope messages.
     *
     * @param enabled             master switch; when false the rule is a no-op.
     * @param threshold           consecutive OTHER turns that trigger the block.
     * @param windowSeconds       sliding TTL on the streak counter — a gap longer than this resets it.
     * @param blockDurationSeconds how long the temporary block lasts (Redis TTL, auto-expires).
     * @param failOpen            when true, a Redis outage lets requests through instead of erroring.
     */
    public record OutOfScope(
            boolean enabled,
            int threshold,
            long windowSeconds,
            long blockDurationSeconds,
            boolean failOpen) {
    }
}
