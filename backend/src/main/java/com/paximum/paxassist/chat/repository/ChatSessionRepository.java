package com.paximum.paxassist.chat.repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.paximum.paxassist.chat.domain.ChatSessionEntity;

public interface ChatSessionRepository extends JpaRepository<ChatSessionEntity, Long> {

    /**
     * Loads a session with its transcript eagerly (fetch join), so the store can map it to the
     * domain model outside any lazy-loading surprises (the app runs with open-in-view=false).
     */
    @Query("select s from ChatSessionEntity s left join fetch s.messages where s.id = :id")
    Optional<ChatSessionEntity> findByIdWithMessages(@Param("id") Long id);

    /**
     * Owner-scoped transcript load — returns empty when the session belongs to another user, so
     * GET/continue can never cross users.
     */
    @Query("select s from ChatSessionEntity s left join fetch s.messages "
            + "where s.id = :id and s.userId = :userId")
    Optional<ChatSessionEntity> findByIdAndUserIdWithMessages(@Param("id") Long id,
                                                              @Param("userId") Long userId);

    boolean existsByIdAndUserId(Long id, Long userId);

    /**
     * Lightweight rows for the history panel — no message bodies, newest first. {@code size(...)}
     * counts the transcript without loading it.
     */
    @Query("select s.id as id, s.title as title, s.updatedAt as updatedAt, "
            + "(select count(m.id) from ChatMessageEntity m where m.session = s) as messageCount "
            + "from ChatSessionEntity s where s.userId = :userId order by s.updatedAt desc")
    List<ChatSessionSummaryView> findSummariesByUserId(@Param("userId") Long userId);

    /** Interface projection backing {@link #findSummariesByUserId(Long)}. */
    interface ChatSessionSummaryView {
        Long getId();

        String getTitle();

        OffsetDateTime getUpdatedAt();

        long getMessageCount();
    }
}
