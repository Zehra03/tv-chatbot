package com.paximum.paxassist.chat.dto;

/**
 * Structured output the LLM is forced to return.
 * Spring AI injects format instructions into the prompt so the model
 * always responds with {"reply": "..."} — never free-form text.
 */
public record AiReply(String reply) {
}
