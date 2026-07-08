package com.paximum.paxassist.chat.dto;

import java.util.List;

/**
 * One chat turn, matching the frontend's {@code ChatMessage} ({@code frontend/src/types/chat.ts}).
 * {@code cards} is populated only on assistant messages that carry search results.
 *
 * @param id        stable id for the frontend list key (DB id for persisted messages, a generated
 *                  id for the freshly-produced POST reply)
 * @param role      "user" | "assistant" | "system"
 * @param content   the message text
 * @param createdAt ISO-8601 instant
 * @param cards     inline result cards, or null/empty when none
 */
public record ChatMessageDto(
        String id,
        String role,
        String content,
        String createdAt,
        List<ResultCardDto> cards) {
}
