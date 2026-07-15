package com.paximum.paxassist.chat.service;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.paximum.paxassist.chat.domain.ChatCaller;
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
    public List<ChatSessionSummaryDto> listSummaries(ChatCaller caller) {
        return summaryRows(caller).stream()
                .map(v -> new ChatSessionSummaryDto(
                        String.valueOf(v.getId()),
                        v.getTitle(),
                        iso(v.getUpdatedAt()),
                        (int) v.getMessageCount()))
                .toList();
    }

    private List<ChatSessionRepository.ChatSessionSummaryView> summaryRows(ChatCaller caller) {
        if (caller.userId() != null) {
            return repository.findSummariesByUserId(caller.userId());
        }
        if (caller.guestToken() != null) {
            return repository.findSummariesByGuestToken(caller.guestToken());
        }
        return List.of();
    }

    @Transactional(readOnly = true)
    public Optional<ChatSessionDto> getSession(String sessionId, ChatCaller caller) {
        Long id = tryParseId(sessionId);
        if (id == null) {
            return Optional.empty();
        }
        if (caller.userId() != null) {
            return repository.findByIdAndUserIdWithMessages(id, caller.userId()).map(this::toDto);
        }
        if (caller.guestToken() != null) {
            return repository.findByIdAndGuestTokenWithMessages(id, caller.guestToken()).map(this::toDto);
        }
        return Optional.empty();
    }

    private ChatSessionDto toDto(ChatSessionEntity entity) {
        List<ChatMessageDto> messages = entity.getMessages().stream()
                .map(this::toMessageDto)
                .toList();
        // The recorded domain scopes the chips to the search the user was actually in. Null for rows
        // written before active_domain existed; the mapper then falls back to guessing from the keys.
        PartialCriteriaDto criteria =
                viewMapper.toPartialCriteria(entity.getAccumulatedCriteria(), lower(entity.getActiveDomain()));
        // pendingQuestion is transient working state (never persisted) → null on a loaded session.
        return new ChatSessionDto(String.valueOf(entity.getId()), entity.getTitle(), messages, criteria, null);
    }

    private ChatMessageDto toMessageDto(ChatMessageEntity message) {
        List<ResultCardDto> cards = viewMapper.toResultCards(message.getResultCards());
        // options (disambiguation card) are transient working state, never persisted → null on GET.
        return new ChatMessageDto(
                String.valueOf(message.getId()),
                message.getRole(),
                message.getContent(),
                iso(message.getCreatedAt()),
                cards.isEmpty() ? null : cards,
                null);
    }

    private String iso(OffsetDateTime value) {
        return (value == null) ? null : value.toInstant().toString();
    }

    /** The session stores the domain as "HOTEL"/"FLIGHT"; the frontend contract uses lower case. */
    private String lower(String value) {
        return (value == null) ? null : value.toLowerCase(Locale.ROOT);
    }

    private Long tryParseId(String sessionId) {
        try {
            return Long.valueOf(sessionId);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
