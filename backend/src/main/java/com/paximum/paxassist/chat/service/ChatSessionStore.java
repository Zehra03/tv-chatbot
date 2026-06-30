package com.paximum.paxassist.chat.service;

import com.paximum.paxassist.chat.domain.ChatSession;

public interface ChatSessionStore {

    /**
     * Returns the existing session for the given id, or creates a new one.
     * Passing null always creates a new session.
     */
    ChatSession getOrCreate(String sessionId);

    void save(ChatSession session);
}
