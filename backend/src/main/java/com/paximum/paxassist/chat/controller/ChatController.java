package com.paximum.paxassist.chat.controller;

import com.paximum.paxassist.chat.dto.ChatResponse;
import com.paximum.paxassist.chat.service.ChatService;
import org.springframework.lang.NonNull;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/chat")
public class ChatController {

    private final ChatService chatService;

    public ChatController(ChatService chatService) {
        this.chatService = chatService;
    }

    @PostMapping
    public ChatResponse chat(@RequestParam("request") @NonNull String request) {
        return new ChatResponse(chatService.chat(request), null, null, false, null);
    }
}