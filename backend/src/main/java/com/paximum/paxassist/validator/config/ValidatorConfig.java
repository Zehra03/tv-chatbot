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
 * Default provider ({@code app.validator.provider=deepseek}) is the official DeepSeek
 * OpenAI-compatible API — chosen over the local Ollama setup after the A/B harness's cost/benefit
 * comparison (Jul 2026: ~+2s/req, ~$0.0002/msg, 92% verdict accuracy with {@code deepseek-chat}).
 * It requires {@code DEEPSEEK_API_KEY} and fails fast at startup without it. Alternatives via
 * {@code VALIDATOR_PROVIDER}: {@code groq} (OpenAI-compatible, LPU-hosted open-weight models, free
 * tier but 6k tokens/min, requires {@code GROQ_API_KEY}) and {@code ollama} — a fully local
 * {@link OllamaChatModel} with no marginal API cost, which the hermetic mock/mock-ai/loadtest
 * profiles pin so tests and CI need no key.
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
