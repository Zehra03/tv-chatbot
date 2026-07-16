package com.paximum.paxassist.chat.service;

import com.paximum.paxassist.chat.domain.ChatCaller;
import com.paximum.paxassist.chat.domain.ChatSession;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class InMemoryChatSessionStore implements ChatSessionStore {

    private final ConcurrentHashMap<String, ChatSession> store = new ConcurrentHashMap<>();

    @Override
    public ChatSession getOrCreate(String sessionId, ChatCaller caller) {
        if (sessionId != null) {
            ChatSession existing = store.get(sessionId);
            if (existing != null && ownedBy(existing, caller)) {
                return existing;
            }
        }
        ChatSession session = new ChatSession(UUID.randomUUID().toString());
        session.setUserId(caller.userId());
        session.setGuestToken(caller.guestToken());
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
    public boolean delete(String sessionId, ChatCaller caller) {
        if (sessionId == null) {
            return false;
        }
        ChatSession existing = store.get(sessionId);
        if (existing == null || !ownedBy(existing, caller)) {
            return false;
        }
        return store.remove(sessionId) != null;
    }

    @Override
    public void save(ChatSession session) {
        store.put(session.getId(), session);
    }

    private boolean ownedBy(ChatSession session, ChatCaller caller) {
        if (caller.userId() != null) {
            return caller.userId().equals(session.getUserId())
                    || (session.getUserId() == null && session.getGuestToken() == null);
        }
        if (caller.guestToken() != null) {
            return caller.guestToken().equals(session.getGuestToken());
        }
        return session.getUserId() == null && session.getGuestToken() == null;
    }
}
