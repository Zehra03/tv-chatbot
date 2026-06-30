package com.paximum.paxassist.chat.service;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;

@Service
public class ChatService {

    private final ChatClient chatClient;

    public ChatService(ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    public String chat(@NonNull String message) {
        String reply = chatClient.prompt()
                .user(message)
                .call()
                .content();
        if (reply == null) {
            throw new IllegalStateException("AI provider returned no content");
        }
        return reply;
    }
}
