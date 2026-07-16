package com.paximum.paxassist.chat.dto;

/**
 * One selectable option on a disambiguation card, matching the frontend's {@code ChoiceOption}
 * ({@code frontend/src/types/chat.ts}). The frontend shows {@code label} as a button; clicking it
 * sends {@code value} as the next user chat turn.
 *
 * @param label human-facing button text
 * @param value message posted as the next user turn when chosen
 */
public record ChoiceOptionDto(String label, String value) {
}
