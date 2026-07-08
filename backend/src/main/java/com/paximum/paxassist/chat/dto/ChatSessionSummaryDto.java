package com.paximum.paxassist.chat.dto;

/**
 * A lightweight row for the history panel, matching the frontend's {@code ChatSessionSummary}
 * ({@code frontend/src/types/chat.ts}). No message bodies — the list stays cheap.
 *
 * @param id           session id
 * @param title        short label (from the first user message), may be null
 * @param updatedAt    ISO-8601 instant of the last activity
 * @param messageCount number of turns in the session
 */
public record ChatSessionSummaryDto(
        String id,
        String title,
        String updatedAt,
        int messageCount) {
}
