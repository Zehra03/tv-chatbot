package com.paximum.paxassist.chat.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.paximum.paxassist.chat.domain.ChatMessage;
import com.paximum.paxassist.chat.domain.ChatMessageEntity;
import com.paximum.paxassist.chat.domain.ChatSession;
import com.paximum.paxassist.chat.domain.ChatSessionEntity;
import com.paximum.paxassist.chat.repository.ChatSessionRepository;

/**
 * Durable, write-through {@link ChatSessionStore}. A live in-memory cache holds the rich working
 * {@link ChatSession} (typed result cards + activeDomain) so FILTER/SELECT keep working across turns
 * exactly as before, while every turn is written through to the DB ({@code chat_sessions} +
 * {@code chat_messages}) for durability.
 *
 * <p>Session ids are the DB-generated {@code bigint} rendered as a String — the server already
 * minted opaque ids before, so the client contract is unchanged. On a cache miss (e.g. after a
 * restart) the session is rebuilt from the DB: transcript and accumulated criteria are restored;
 * typed result cards are not (they are transient by design), so FILTER/SELECT stay degraded until
 * the next search.
 */
@Primary
@Component
public class JpaChatSessionStore implements ChatSessionStore {

    private final ChatSessionRepository repository;
    private final ConcurrentHashMap<String, ChatSession> liveCache = new ConcurrentHashMap<>();

    public JpaChatSessionStore(ChatSessionRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional
    public ChatSession getOrCreate(String sessionId, Long userId) {
        if (sessionId != null) {
            ChatSession cached = liveCache.get(sessionId);
            if (cached != null && ownedBy(cached, userId)) {
                return cached;
            }
            Long id = tryParseId(sessionId);
            if (id != null) {
                Optional<ChatSession> loaded = repository.findByIdAndUserIdWithMessages(id, userId)
                        .map(this::toDomain);
                if (loaded.isPresent()) {
                    liveCache.put(loaded.get().getId(), loaded.get());
                    return loaded.get();
                }
            }
            // Unknown / unparseable / foreign id → fall through and mint a fresh owned session.
        }
        ChatSessionEntity entity = new ChatSessionEntity(new HashMap<>());
        entity.setUserId(userId);
        ChatSessionEntity created = repository.save(entity);
        ChatSession session = new ChatSession(String.valueOf(created.getId()));
        session.setUserId(userId);
        liveCache.put(session.getId(), session);
        return session;
    }

    private boolean ownedBy(ChatSession session, Long userId) {
        return session.getUserId() == null || session.getUserId().equals(userId);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ChatSession> find(String sessionId) {
        if (sessionId == null) {
            return Optional.empty();
        }
        ChatSession cached = liveCache.get(sessionId);
        if (cached != null) {
            return Optional.of(cached);
        }
        Long id = tryParseId(sessionId);
        if (id == null) {
            return Optional.empty();
        }
        return repository.findByIdWithMessages(id).map(entity -> {
            ChatSession domain = toDomain(entity);
            liveCache.put(domain.getId(), domain);
            return domain;
        });
    }

    @Override
    @Transactional
    public boolean delete(String sessionId, Long userId) {
        if (sessionId == null) {
            return false;
        }
        Long id = tryParseId(sessionId);
        if (id == null || !repository.existsByIdAndUserId(id, userId)) {
            return false;
        }
        liveCache.remove(sessionId);
        repository.deleteById(id);
        return true;
    }

    @Override
    @Transactional
    public void save(ChatSession session) {
        Long id = tryParseId(session.getId());
        if (id == null) {
            return; // sessions minted here always have a numeric id; nothing to persist otherwise
        }
        ChatSessionEntity entity = repository.findByIdWithMessages(id).orElse(null);
        if (entity == null) {
            return;
        }
        entity.setAccumulatedCriteria(new HashMap<>(session.getAccumulatedCriteria()));
        if (entity.getTitle() == null) {
            entity.setTitle(deriveTitle(session.getMessages()));
        }

        // Append only messages not yet persisted (the entity's message count is the persisted count).
        List<ChatMessage> messages = session.getMessages();
        int persisted = entity.getMessages().size();
        List<Object> lastCards = session.getLastResultCards();
        for (int i = persisted; i < messages.size(); i++) {
            ChatMessage message = messages.get(i);
            boolean lastAssistant = (i == messages.size() - 1) && "assistant".equals(message.role());
            List<Object> cards = (lastAssistant && lastCards != null && !lastCards.isEmpty())
                    ? new ArrayList<>(lastCards)
                    : null;
            entity.addMessage(message.role(), message.content(), cards);
        }
        repository.save(entity);
        liveCache.put(session.getId(), session);
    }

    private ChatSession toDomain(ChatSessionEntity entity) {
        ChatSession session = new ChatSession(String.valueOf(entity.getId()));
        session.setUserId(entity.getUserId());
        session.setAccumulatedCriteria(new HashMap<>(entity.getAccumulatedCriteria()));
        List<Object> lastCards = new ArrayList<>();
        for (ChatMessageEntity message : entity.getMessages()) {
            session.addMessage(message.getRole(), message.getContent());
            if (message.getResultCards() != null && !message.getResultCards().isEmpty()) {
                lastCards = new ArrayList<>(message.getResultCards());
            }
        }
        session.setLastResultCards(lastCards);
        return session;
    }

    /** Session label for the history panel: the first user message, trimmed to a short line. */
    private String deriveTitle(List<ChatMessage> messages) {
        for (ChatMessage message : messages) {
            if ("user".equals(message.role()) && message.content() != null && !message.content().isBlank()) {
                String text = message.content().strip();
                return text.length() > 60 ? text.substring(0, 60).strip() + "…" : text;
            }
        }
        return null;
    }

    private Long tryParseId(String sessionId) {
        try {
            return Long.valueOf(sessionId);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
