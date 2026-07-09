package com.paximum.paxassist.chat.service;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.paximum.paxassist.chat.domain.ChatSession;
import com.paximum.paxassist.chat.dto.ChatMessageDto;
import com.paximum.paxassist.chat.dto.ChoiceOptionDto;
import com.paximum.paxassist.chat.dto.PartialCriteriaDto;
import com.paximum.paxassist.chat.dto.ResultCardDto;
import com.paximum.paxassist.chat.dto.SendMessageResponseDto;
import com.paximum.paxassist.orchestrator.ChoiceOption;
import com.paximum.paxassist.orchestrator.OrchestrationResult;

/**
 * Assembles the frontend-shaped POST response from a completed orchestrator turn. Keeps this
 * mapping out of the thin {@code ChatController} (project guardrail: controllers stay thin).
 *
 * <p>The reply's {@code id}/{@code createdAt} are generated here (the frontend only needs
 * uniqueness within the live thread + a timestamp). A later GET of the same session returns the
 * persisted DB ids/timestamps; because loading a session replaces the whole thread client-side,
 * the two id schemes never clash.
 */
@Component
public class ChatResponseAssembler {

    private final ChatViewMapper viewMapper;

    public ChatResponseAssembler(ChatViewMapper viewMapper) {
        this.viewMapper = viewMapper;
    }

    public SendMessageResponseDto toResponse(OrchestrationResult result, ChatSession session) {
        List<ResultCardDto> cards = viewMapper.toResultCards(result.cards());
        List<ChoiceOptionDto> options = toChoiceOptions(result.options());
        ChatMessageDto reply = new ChatMessageDto(
                UUID.randomUUID().toString(),
                "assistant",
                result.reply(),
                Instant.now().toString(),
                cards.isEmpty() ? null : cards,
                options.isEmpty() ? null : options);

        String domain = (result.domain() != null) ? result.domain() : lower(session.getActiveDomain());
        PartialCriteriaDto criteria = viewMapper.toPartialCriteria(session.getAccumulatedCriteria(), domain);

        return new SendMessageResponseDto(result.sessionId(), reply, criteria, result.pendingQuestion());
    }

    private List<ChoiceOptionDto> toChoiceOptions(List<ChoiceOption> options) {
        if (options == null || options.isEmpty()) {
            return List.of();
        }
        return options.stream()
                .map(o -> new ChoiceOptionDto(o.label(), o.value()))
                .toList();
    }

    private String lower(String value) {
        return (value == null) ? null : value.toLowerCase(Locale.ROOT);
    }
}
