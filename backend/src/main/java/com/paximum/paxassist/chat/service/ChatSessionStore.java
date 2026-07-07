package com.paximum.paxassist.chat.service;

import com.paximum.paxassist.chat.domain.ChatSession;

import java.util.Optional;

public interface ChatSessionStore {

    /**
     * Returns the existing session for the given id when it belongs to {@code userId}, otherwise
     * creates a new session owned by {@code userId}. Passing a null id — or an id owned by another
     * user / unknown — always mints a fresh session, so a user can never continue someone else's
     * conversation.
     */
    ChatSession getOrCreate(String sessionId, Long userId);

    /**
     * Returns the session for the given id without creating one.
     * Empty if the id is null or unknown.
     */
    Optional<ChatSession> find(String sessionId);

    /**
     * Removes the session with the given id when it belongs to {@code userId}.
     * @return true if an owned session existed and was removed, false otherwise.
     */
    boolean delete(String sessionId, Long userId);

    void save(ChatSession session);
}
