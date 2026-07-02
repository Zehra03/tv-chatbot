package com.paximum.paxassist.chat.controller;

import com.paximum.paxassist.chat.domain.ChatMessage;
import com.paximum.paxassist.chat.domain.ChatSession;
import com.paximum.paxassist.chat.dto.ChatHistoryResponse;
import com.paximum.paxassist.chat.dto.ChatRequest;
import com.paximum.paxassist.chat.dto.ChatResponse;
import com.paximum.paxassist.chat.service.ChatService;
import com.paximum.paxassist.chat.service.ChatSessionStore;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

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
        session.addMessage(ChatMessage.user(request.message()));
        session.addMessage(ChatMessage.assistant(aiReply.reply()));
        sessionStore.save(session);
        return new ChatResponse(aiReply.reply(), session.getId(), null, false, null);
    }

    @GetMapping("/{sessionId}")
    public ResponseEntity<ChatHistoryResponse> history(@PathVariable String sessionId) {
        return sessionStore.findById(sessionId)
                .map(session -> ResponseEntity.ok(new ChatHistoryResponse(session.getId(), session.getMessages())))
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> streamChat(@RequestBody @Valid ChatRequest request) {
        sessionStore.getOrCreate(request.sessionId());
        return chatService.streamChat(request.message());
    }
}