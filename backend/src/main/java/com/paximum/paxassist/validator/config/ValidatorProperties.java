package com.paximum.paxassist.validator.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Deliberately independent of the {@code chat} module's AI configuration: the Validator always
 * picks its own local model, regardless of what the main chatbot's engine (Gemini or otherwise) is.
 */
@ConfigurationProperties(prefix = "app.validator")
public record ValidatorProperties(
        boolean enabled,
        boolean abTestEnabled,
        int maxRetries,
        double temperature,
        int maxTokens,
        String model,
        // "ollama" (default, fully local) or "groq" — experimental, used by the A/B harness to
        // compare cloud-hosted candidate models against the local validator. See ValidatorConfig.
        String provider) {
}
