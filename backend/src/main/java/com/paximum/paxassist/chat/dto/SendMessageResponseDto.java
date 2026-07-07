package com.paximum.paxassist.chat.dto;

/**
 * Response to POST {@code /api/v1/chat}, matching the frontend's {@code SendMessageResponse}
 * ({@code frontend/src/api/chatApi.ts}). The assistant {@code reply} is a full message object
 * (with any result cards nested under {@code reply.cards}), NOT a bare string.
 *
 * @param sessionId           the (possibly newly created) session id to continue the conversation
 * @param reply               the assistant's message for this turn
 * @param accumulatedCriteria criteria gathered so far, or null when none/unknown domain
 * @param pendingQuestion     the clarifying question awaiting an answer, or null when complete
 */
public record SendMessageResponseDto(
        String sessionId,
        ChatMessageDto reply,
        PartialCriteriaDto accumulatedCriteria,
        String pendingQuestion) {
}
