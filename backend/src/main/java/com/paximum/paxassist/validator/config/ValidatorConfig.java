package com.paximum.paxassist.validator.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.ollama.api.OllamaOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Provisions a {@link ChatClient} that is wired to its own local Ollama-backed {@link OllamaChatModel},
 * independent of whatever {@code ChatClient}/model the {@code chat} module ends up using (that module is
 * migrating to the Gemini API). This keeps the Validator local even after that migration lands, since it
 * never shares a bean with the main chat engine.
 */
@Configuration
@EnableConfigurationProperties(ValidatorProperties.class)
public class ValidatorConfig {

    @Bean
    public ChatClient validatorChatClient(@Value("${OLLAMA_BASE_URL:http://localhost:11434}") String ollamaBaseUrl,
                                           ValidatorProperties validatorProperties) {
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
}
