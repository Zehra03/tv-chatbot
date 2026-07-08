package com.paximum.paxassist.orchestrator.refine;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Registers {@link EvaluatorProperties} as a bean, matching the codebase convention
 * (see {@code RateLimitRedisConfig} / {@code FlightFeignConfig}).
 */
@Configuration
@EnableConfigurationProperties(EvaluatorProperties.class)
public class EvaluatorConfig {
}
