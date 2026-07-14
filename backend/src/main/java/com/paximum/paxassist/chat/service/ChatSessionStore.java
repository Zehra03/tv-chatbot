package com.paximum.paxassist.chat.service;

import com.paximum.paxassist.chat.domain.ChatCaller;
import com.paximum.paxassist.chat.domain.ChatSession;

import java.util.Optional;

public interface ChatSessionStore {

    /**
     * Returns the existing session for the given id when it belongs to {@code caller} (matched by
     * userId for a logged-in user, or by guest token for a guest), otherwise creates a new session
     * owned by {@code caller}. Passing a null id — or an id owned by someone else / unknown — always
     * mints a fresh session, so a caller can never continue someone else's conversation.
     */
    ChatSession getOrCreate(String sessionId, ChatCaller caller);

    /**
     * Returns the session for the given id without creating one.
     * Empty if the id is null or unknown.
     */
    Optional<ChatSession> find(String sessionId);

    /**
     * Removes the session with the given id when it belongs to {@code caller}.
     * @return true if an owned session existed and was removed, false otherwise.
     */
    boolean delete(String sessionId, ChatCaller caller);

    void save(ChatSession session);
}
