package com.paximum.paxassist.chat.domain;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

/**
 * JPA persistence model for the {@code chat_sessions} table (V1 schema). Kept separate from the
 * in-memory working model {@link ChatSession} so JPA concerns never leak into the orchestrator;
 * {@code JpaChatSessionStore} maps between the two.
 *
 * <p>{@code accumulated_criteria} is the durable slot-filling state (jsonb). {@code created_at}/
 * {@code updated_at} are owned by the DB (default {@code now()} + trigger), so they are read-only here.
 */
@Entity
@Table(name = "chat_sessions")
public class ChatSessionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id")
    private Long userId;

    @Column(length = 200)
    private String title;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "accumulated_criteria", nullable = false)
    private Map<String, Object> accumulatedCriteria = new HashMap<>();

    @Column(name = "created_at", insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private OffsetDateTime updatedAt;

    @OneToMany(mappedBy = "session", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id asc")
    private List<ChatMessageEntity> messages = new ArrayList<>();

    protected ChatSessionEntity() {
        // JPA
    }

    public ChatSessionEntity(Map<String, Object> accumulatedCriteria) {
        this.accumulatedCriteria = (accumulatedCriteria != null) ? accumulatedCriteria : new HashMap<>();
    }

    public ChatMessageEntity addMessage(String role, String content, List<Object> resultCards) {
        ChatMessageEntity message = new ChatMessageEntity(this, role, content, resultCards);
        messages.add(message);
        return message;
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public Map<String, Object> getAccumulatedCriteria() {
        return accumulatedCriteria;
    }

    public void setAccumulatedCriteria(Map<String, Object> accumulatedCriteria) {
        this.accumulatedCriteria = (accumulatedCriteria != null) ? accumulatedCriteria : new HashMap<>();
    }

    public List<ChatMessageEntity> getMessages() {
        return messages;
    }
}
