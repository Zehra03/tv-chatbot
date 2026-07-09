package com.paximum.paxassist.ai;

import org.springframework.lang.NonNull;

/**
 * A single turn in the conversation history passed to the intent extraction LLM.
 * role: "user" | "assistant"
 */
public record ChatHistoryEntry(@NonNull String role, @NonNull String content) {
}
