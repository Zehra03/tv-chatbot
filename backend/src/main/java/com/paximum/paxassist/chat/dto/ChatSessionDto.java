package com.paximum.paxassist.chat.dto;

import java.util.List;

/**
 * A full session with its transcript, matching the frontend's {@code ChatSession}
 * ({@code frontend/src/types/chat.ts}) — used to rehydrate a conversation from the history panel.
 *
 * <p>{@code pendingQuestion} is transient working state (not persisted), so it is always null here;
 * the frontend treats it as optional.
 *
 * @param id                  session id
 * @param title               short label for the history list (derived from the first user message)
 * @param messages            the ordered transcript
 * @param accumulatedCriteria criteria gathered so far, or null when none/unknown domain
 * @param pendingQuestion     always null on a loaded session (transient)
 */
public record ChatSessionDto(
        String id,
        String title,
        List<ChatMessageDto> messages,
        PartialCriteriaDto accumulatedCriteria,
        String pendingQuestion) {
}
