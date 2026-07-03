package com.paximum.paxassist.ai;

/**
 * Structured JSON output returned by the intent extraction LLM call.
 * Spring AI's BeanOutputConverter injects schema instructions into the prompt
 * so the model is forced to respond in this exact format.
 */
public record IntentExtractionResult(
        IntentType intent,
        SlotCriteria criteria
) {
}
