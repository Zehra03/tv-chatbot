package com.paximum.paxassist.chat.domain;

import java.time.OffsetDateTime;
import java.util.List;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * JPA persistence model for the {@code chat_messages} table (V1 schema): the append-only transcript.
 * {@code result_cards} holds an optional inline display snapshot (jsonb); {@code created_at} is
 * DB-owned.
 */
@Entity
@Table(name = "chat_messages")
public class ChatMessageEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = jakarta.persistence.FetchType.LAZY)
    @JoinColumn(name = "session_id", nullable = false)
    private ChatSessionEntity session;

    @Column(nullable = false, length = 16)
    private String role;

    @Column(nullable = false, columnDefinition = "text")
    private String content;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "result_cards")
    private List<Object> resultCards;

    @Column(name = "created_at", insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    protected ChatMessageEntity() {
        // JPA
    }

    public ChatMessageEntity(ChatSessionEntity session, String role, String content, List<Object> resultCards) {
        this.session = session;
        this.role = role;
        this.content = content;
        this.resultCards = resultCards;
    }

    public Long getId() {
        return id;
    }

    public String getRole() {
        return role;
    }

    public String getContent() {
        return content;
    }

    public List<Object> getResultCards() {
        return resultCards;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}
