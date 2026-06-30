package com.paximum.paxassist.chat.controller;

import com.paximum.paxassist.chat.dto.ChatRequest;
import com.paximum.paxassist.chat.dto.ChatResponse;
import com.paximum.paxassist.chat.service.ChatService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/chat")
public class ChatController {

    private final ChatService chatService;

    public ChatController(ChatService chatService) {
        this.chatService = chatService;
    }

    @PostMapping
    public ChatResponse chat(@RequestBody @Valid ChatRequest request) {
        return new ChatResponse(chatService.chat(request.message()), null, null, false, null);
    }
}