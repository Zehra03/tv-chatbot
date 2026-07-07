package com.paximum.paxassist.chat.service;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.paximum.paxassist.chat.domain.ChatMessageEntity;
import com.paximum.paxassist.chat.domain.ChatSessionEntity;
import com.paximum.paxassist.chat.dto.ChatMessageDto;
import com.paximum.paxassist.chat.dto.ChatSessionDto;
import com.paximum.paxassist.chat.dto.ChatSessionSummaryDto;
import com.paximum.paxassist.chat.dto.PartialCriteriaDto;
import com.paximum.paxassist.chat.dto.ResultCardDto;
import com.paximum.paxassist.chat.repository.ChatSessionRepository;

/**
 * Read side for the history panel and conversation rehydration. Reads persistence entities
 * directly (not the lossy in-memory {@link com.paximum.paxassist.chat.domain.ChatSession}) so it
 * can surface each message's DB id, timestamp and result cards. Every query is scoped to the
 * authenticated user, so one user can never list or open another's sessions.
 */
@Service
public class ChatSessionQueryService {

    private final ChatSessionRepository repository;
    private final ChatViewMapper viewMapper;

    public ChatSessionQueryService(ChatSessionRepository repository, ChatViewMapper viewMapper) {
        this.repository = repository;
        this.viewMapper = viewMapper;
    }

    @Transactional(readOnly = true)
    public List<ChatSessionSummaryDto> listSummaries(Long userId) {
        return repository.findSummariesByUserId(userId).stream()
                .map(v -> new ChatSessionSummaryDto(
                        String.valueOf(v.getId()),
                        v.getTitle(),
                        iso(v.getUpdatedAt()),
                        (int) v.getMessageCount()))
                .toList();
    }

    @Transactional(readOnly = true)
    public Optional<ChatSessionDto> getSession(String sessionId, Long userId) {
        Long id = tryParseId(sessionId);
        if (id == null) {
            return Optional.empty();
        }
        return repository.findByIdAndUserIdWithMessages(id, userId).map(this::toDto);
    }

    private ChatSessionDto toDto(ChatSessionEntity entity) {
        List<ChatMessageDto> messages = entity.getMessages().stream()
                .map(this::toMessageDto)
                .toList();
        PartialCriteriaDto criteria = viewMapper.toPartialCriteria(entity.getAccumulatedCriteria(), null);
        // pendingQuestion is transient working state (never persisted) → null on a loaded session.
        return new ChatSessionDto(String.valueOf(entity.getId()), entity.getTitle(), messages, criteria, null);
    }

    private ChatMessageDto toMessageDto(ChatMessageEntity message) {
        List<ResultCardDto> cards = viewMapper.toResultCards(message.getResultCards());
        return new ChatMessageDto(
                String.valueOf(message.getId()),
                message.getRole(),
                message.getContent(),
                iso(message.getCreatedAt()),
                cards.isEmpty() ? null : cards);
    }

    private String iso(OffsetDateTime value) {
        return (value == null) ? null : value.toInstant().toString();
    }

    private Long tryParseId(String sessionId) {
        try {
            return Long.valueOf(sessionId);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
