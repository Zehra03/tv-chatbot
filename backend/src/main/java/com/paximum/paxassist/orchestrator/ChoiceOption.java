package com.paximum.paxassist.orchestrator;

/**
 * One selectable option on a disambiguation ("hangisini demek istediniz?") card. Produced by an
 * {@link com.paximum.paxassist.orchestrator.intent.IntentHandler} when the same input has two or
 * more competing interpretations the system can enumerate (e.g. a bare place name that could mean
 * a hotel search or a flight search).
 *
 * <p>The frontend renders {@code label} as a button; clicking it sends {@code value} back as the
 * user's next chat turn — i.e. disambiguation reuses the normal chat pipeline, no special endpoint
 * (see {@code docs/chatbot-test-senaryolari.md} §6).
 *
 * @param label the human-facing button text (e.g. "Otel araması")
 * @param value the message posted as the next user turn when this option is chosen
 *              (e.g. "Antalya için otel arıyorum")
 */
public record ChoiceOption(String label, String value) {
}
