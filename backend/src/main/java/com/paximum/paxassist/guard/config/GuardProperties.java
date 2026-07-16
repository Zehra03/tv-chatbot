package com.paximum.paxassist.guard.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration for the stateful guard rules that need per-user state (unlike the stateless
 * regex checks in {@code GuardRuleService}, which need no config).
 *
 * @param outOfScope        tuning for the consecutive out-of-scope (OTHER intent) abuse block.
 * @param maxMessageLength  longest accepted message, in characters. Must stay in step with the
 *                          {@code @Size} cap on {@code ChatRequest.message}: the DTO rejects an
 *                          oversized body at the HTTP boundary, and the guard is the second layer
 *                          for any caller that reaches the pipeline by another route. Falls back to
 *                          {@link #DEFAULT_MAX_MESSAGE_LENGTH} when unset or non-positive, so a
 *                          missing/typo'd property can never silently disable the rule.
 */
@ConfigurationProperties(prefix = "app.guard")
public record GuardProperties(OutOfScope outOfScope, Integer maxMessageLength) {

    public static final int DEFAULT_MAX_MESSAGE_LENGTH = 2000;

    public GuardProperties {
        if (maxMessageLength == null || maxMessageLength <= 0) {
            maxMessageLength = DEFAULT_MAX_MESSAGE_LENGTH;
        }
    }

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
