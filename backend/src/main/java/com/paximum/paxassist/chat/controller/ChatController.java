package com.paximum.paxassist.chat.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.paximum.paxassist.chat.domain.ChatSession;
import com.paximum.paxassist.chat.dto.ChatRequest;
import com.paximum.paxassist.chat.dto.ChatResponse;
import com.paximum.paxassist.chat.service.ChatService;
import com.paximum.paxassist.chat.service.ChatSessionStore;

import jakarta.validation.Valid;

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

    @GetMapping("/{sessionId}")
    public ResponseEntity<ChatResponse> getSession(@PathVariable String sessionId) {
        return sessionStore.find(sessionId)
                .map(session -> ResponseEntity.ok(new ChatResponse(
                        null, session.getId(), session.getLastResultCards(), false, null)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{sessionId}")
    public ResponseEntity<Void> deleteSession(@PathVariable String sessionId) {
        return sessionStore.delete(sessionId)
                ? ResponseEntity.noContent().build()
                : ResponseEntity.notFound().build();
    }
}
