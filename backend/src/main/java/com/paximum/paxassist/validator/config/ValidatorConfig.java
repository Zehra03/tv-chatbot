package com.paximum.paxassist.validator.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.ollama.api.OllamaOptions;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Provisions the Validator's {@link ChatClient}, independent of whatever {@code ChatClient}/model the
 * {@code chat} module ends up using (that module is migrating to the Gemini API) — the Validator never
 * shares a bean with the main chat engine.
 *
 * Default provider ({@code app.validator.provider=ollama}) is a fully local {@link OllamaChatModel} —
 * no marginal API cost. {@code provider=groq} and {@code provider=deepseek} are EXPERIMENTAL
 * alternatives added for the A/B harness's cost/benefit comparison: both point the same
 * {@code ChatClient} contract at an OpenAI-compatible endpoint (Groq: LPU-hosted open-weight models,
 * free tier but 6k tokens/min; DeepSeek: official API, pay-as-you-go, no hard rate limit). They
 * require {@code GROQ_API_KEY} / {@code DEEPSEEK_API_KEY} respectively. Not wired into any default
 * profile; opt-in via {@code VALIDATOR_PROVIDER}.
 */
@Configuration
@EnableConfigurationProperties(ValidatorProperties.class)
public class ValidatorConfig {

    private static final String GROQ_BASE_URL = "https://api.groq.com/openai";
    private static final String DEEPSEEK_BASE_URL = "https://api.deepseek.com";

    @Bean
    public ChatClient validatorChatClient(@Value("${OLLAMA_BASE_URL:http://localhost:11434}") String ollamaBaseUrl,
                                           @Value("${GROQ_API_KEY:}") String groqApiKey,
                                           @Value("${DEEPSEEK_API_KEY:}") String deepseekApiKey,
                                           ValidatorProperties validatorProperties) {
        return switch (validatorProperties.provider() == null ? "ollama"
                : validatorProperties.provider().toLowerCase()) {
            case "groq" -> openAiCompatibleChatClient(GROQ_BASE_URL, groqApiKey, "GROQ_API_KEY",
                    validatorProperties);
            case "deepseek" -> openAiCompatibleChatClient(DEEPSEEK_BASE_URL, deepseekApiKey, "DEEPSEEK_API_KEY",
                    validatorProperties);
            default -> ollamaChatClient(ollamaBaseUrl, validatorProperties);
        };
    }

    private ChatClient ollamaChatClient(String ollamaBaseUrl, ValidatorProperties validatorProperties) {
        OllamaApi ollamaApi = OllamaApi.builder()
                .baseUrl(ollamaBaseUrl)
                .build();

        OllamaChatModel ollamaChatModel = OllamaChatModel.builder()
                .ollamaApi(ollamaApi)
                .defaultOptions(OllamaOptions.builder()
                        .model(validatorProperties.model())
                        .temperature(validatorProperties.temperature())
                        .numPredict(validatorProperties.maxTokens())
                        .build())
                .build();

        return ChatClient.builder(ollamaChatModel).build();
    }

    private ChatClient openAiCompatibleChatClient(String baseUrl, String apiKey, String apiKeyEnvName,
                                                   ValidatorProperties validatorProperties) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("app.validator.provider=" + validatorProperties.provider()
                    + " requires " + apiKeyEnvName + " to be set");
        }
        OpenAiApi api = OpenAiApi.builder()
                .baseUrl(baseUrl)
                .apiKey(apiKey)
                .build();

        OpenAiChatModel chatModel = OpenAiChatModel.builder()
                .openAiApi(api)
                .defaultOptions(OpenAiChatOptions.builder()
                        .model(validatorProperties.model())
                        .temperature(validatorProperties.temperature())
                        .maxTokens(validatorProperties.maxTokens())
                        .build())
                .build();

        return ChatClient.builder(chatModel).build();
    }
}
