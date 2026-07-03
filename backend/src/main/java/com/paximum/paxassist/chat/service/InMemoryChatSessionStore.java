package com.paximum.paxassist.chat.service;

import com.paximum.paxassist.chat.domain.ChatSession;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class InMemoryChatSessionStore implements ChatSessionStore {

    private final ConcurrentHashMap<String, ChatSession> store = new ConcurrentHashMap<>();

    @Override
    public ChatSession getOrCreate(String sessionId) {
        if (sessionId != null && store.containsKey(sessionId)) {
            return store.get(sessionId);
        }
        ChatSession session = new ChatSession(UUID.randomUUID().toString());
        store.put(session.getId(), session);
        return session;
    }

    @Override
    public Optional<ChatSession> find(String sessionId) {
        if (sessionId == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(store.get(sessionId));
    }

    @Override
    public boolean delete(String sessionId) {
        if (sessionId == null) {
            return false;
        }
        return store.remove(sessionId) != null;
    }

    @Override
    public void save(ChatSession session) {
        store.put(session.getId(), session);
    }
}
