package com.paximum.paxassist.chat.dto;

import java.util.List;

/**
 * One chat turn, matching the frontend's {@code ChatMessage} ({@code frontend/src/types/chat.ts}).
 * {@code cards} is populated only on assistant messages that carry search results; {@code options}
 * only on a disambiguation ("hangisini demek istediniz?") turn. Both are transient display state —
 * {@code options} is never persisted, so a session reloaded via GET has it null.
 *
 * @param id        stable id for the frontend list key (DB id for persisted messages, a generated
 *                  id for the freshly-produced POST reply)
 * @param role      "user" | "assistant" | "system"
 * @param content   the message text
 * @param createdAt ISO-8601 instant
 * @param cards     inline result cards, or null/empty when none
 * @param options   disambiguation options, or null/empty when none
 */
public record ChatMessageDto(
        String id,
        String role,
        String content,
        String createdAt,
        List<ResultCardDto> cards,
        List<ChoiceOptionDto> options) {
}
