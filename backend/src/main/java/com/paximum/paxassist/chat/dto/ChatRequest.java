package com.paximum.paxassist.chat.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.lang.NonNull;

/**
 * A single chat turn from the client. {@code message} is bounded so an oversized payload is rejected
 * at the boundary (400) before it ever reaches the guard, the LLM extractor or the chat model —
 * unbounded input is a cost/token trap the 30-req/min rate limit does not stop on its own.
 */
public record ChatRequest(
        String sessionId,
        @NotBlank
        @NonNull
        @Size(max = 2000, message = "Mesaj çok uzun. Lütfen 2000 karakteri aşmayın.")
        String message) {
}
