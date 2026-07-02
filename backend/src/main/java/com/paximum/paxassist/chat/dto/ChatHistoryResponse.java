package com.paximum.paxassist.chat.dto;

import com.paximum.paxassist.chat.domain.ChatMessage;

import java.util.List;

public record ChatHistoryResponse(String sessionId, List<ChatMessage> messages) {
}
