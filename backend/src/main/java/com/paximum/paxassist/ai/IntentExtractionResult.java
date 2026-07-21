package com.paximum.paxassist.ai;

/**
 * Structured JSON output returned by the intent extraction LLM call.
 * Spring AI's BeanOutputConverter injects schema instructions into the prompt
 * so the model is forced to respond in this exact format.
 *
 * <p>{@code detectedLanguage} / {@code languageConfidence} are recomputed on every user message
 * (never fixed for the session): they carry which language the reply should be produced in, without
 * touching {@link SlotCriteria}, whose values stay language-neutral and standard-formatted.
 *
 * @param detectedLanguage   ISO 639-1 code of the current user message (e.g. "tr", "en", "de"),
 *                           or null on code paths that skip the model (bare greeting, parse failure)
 * @param languageConfidence how sure the model is about {@code detectedLanguage}; see
 *                           {@link LanguageConfidence}. Null on the model-free code paths.
 */
public record IntentExtractionResult(
        IntentType intent,
        SlotCriteria criteria,
        String detectedLanguage,
        LanguageConfidence languageConfidence
) {

    /**
     * Shorthand for the code paths that never run the language-detecting model (bare greeting,
     * parse-failure fallback): leaves both language fields null. Keeps existing two-argument callers
     * compiling; the canonical four-argument constructor stays the schema/deserialization source.
     */
    public IntentExtractionResult(IntentType intent, SlotCriteria criteria) {
        this(intent, criteria, null, null);
    }
}
