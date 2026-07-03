package com.paximum.paxassist.chat.service;

import com.paximum.paxassist.chat.domain.ChatSession;

import java.util.Optional;

public interface ChatSessionStore {

    /**
     * Returns the existing session for the given id, or creates a new one.
     * Passing null always creates a new session.
     */
    ChatSession getOrCreate(String sessionId);

    /**
     * Returns the session for the given id without creating one.
     * Empty if the id is null or unknown.
     */
    Optional<ChatSession> find(String sessionId);

    /**
     * Removes the session with the given id.
     * @return true if a session existed and was removed, false otherwise.
     */
    boolean delete(String sessionId);

    void save(ChatSession session);
}
