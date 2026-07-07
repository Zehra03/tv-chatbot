package com.paximum.paxassist.chat.domain;

/**
 * A single turn stored on the in-memory {@link ChatSession}.
 * Deliberately a chat-local type (role/content) so {@code chat.domain} does NOT
 * depend on the {@code ai} module's DTOs — the orchestrator converts to
 * {@code ai.ChatHistoryEntry} when it needs to feed history to intent extraction.
 * Aligns with the future {@code chat_messages} table (role, content columns).
 *
 * @param role    "user" | "assistant" | "system"
 * @param content the message text
 */
public record ChatMessage(String role, String content) {
}
