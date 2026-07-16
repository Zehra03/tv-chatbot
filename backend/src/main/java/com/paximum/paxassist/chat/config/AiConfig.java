    package com.paximum.paxassist.chat.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.lang.NonNull;

@Configuration
public class AiConfig {

    @Bean
    ChatClient chatClient(@NonNull ChatModel chatModel) {
        return ChatClient.builder(chatModel)
                .defaultSystem(PaxiSystemPrompt.forUser(null))
                .build();
    }

    @Bean
    @org.springframework.context.annotation.Profile({"mock", "mock-ai"})
    ChatModel mockChatModel() {
        return new ChatModel() {
            @Override
            public org.springframework.ai.chat.model.ChatResponse call(org.springframework.ai.chat.prompt.Prompt prompt) {
                return new org.springframework.ai.chat.model.ChatResponse(java.util.List.of());
            }

            @Override
            public org.springframework.ai.chat.prompt.ChatOptions getDefaultOptions() {
                return null;
            }
        };
    }
}
