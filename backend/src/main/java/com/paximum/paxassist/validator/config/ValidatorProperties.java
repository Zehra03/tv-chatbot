package com.paximum.paxassist.validator.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Deliberately independent of the {@code chat} module's AI configuration: the Validator always
 * picks its own model, regardless of what the main chatbot's engine (Gemini or otherwise) is.
 */
@ConfigurationProperties(prefix = "app.validator")
public record ValidatorProperties(
        boolean enabled,
        boolean abTestEnabled,
        int maxRetries,
        double temperature,
        int maxTokens,
        String model,
        // "deepseek" (default, requires DEEPSEEK_API_KEY), "groq" (requires GROQ_API_KEY) or
        // "ollama" (fully local; pinned by the hermetic test profiles). See ValidatorConfig.
        String provider) {
}
