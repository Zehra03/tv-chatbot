package com.paximum.paxassist.chat.controller;

import com.paximum.paxassist.chat.domain.ChatSession;
import com.paximum.paxassist.chat.dto.ChatRequest;
import com.paximum.paxassist.chat.dto.ChatResponse;
import com.paximum.paxassist.chat.service.ChatService;
import com.paximum.paxassist.chat.service.ChatSessionStore;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/chat")
public class ChatController {

    private final ChatService chatService;
    private final ChatSessionStore sessionStore;

    public ChatController(ChatService chatService, ChatSessionStore sessionStore) {
        this.chatService = chatService;
        this.sessionStore = sessionStore;
    }

    @PostMapping
    public ChatResponse chat(@RequestBody @Valid ChatRequest request) {
        ChatSession session = sessionStore.getOrCreate(request.sessionId());
        var aiReply = chatService.chat(request.message());
        return new ChatResponse(aiReply.reply(), session.getId(), null, false, null);
    }
}