package com.paximum.paxassist.guard.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Enables {@link GuardProperties} binding for the guard module. Mirrors {@code ValidatorConfig}'s
 * {@code @EnableConfigurationProperties} wiring.
 */
@Configuration
@EnableConfigurationProperties(GuardProperties.class)
public class GuardConfig {
}
