package com.paximum.paxassist.chat.service;

import com.paximum.paxassist.chat.domain.ChatSession;

import java.util.Optional;

public interface ChatSessionStore {

    ChatSession getOrCreate(String sessionId);

    Optional<ChatSession> findById(String sessionId);

    void save(ChatSession session);
}
