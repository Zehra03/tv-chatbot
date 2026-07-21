package com.paximum.paxassist.chat.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

/**
 * Localizes the assistant's deterministic, natural-language reply text into the language the user
 * is currently writing in, so the orchestrator's template replies (slot-filling questions, result
 * headers, error messages) reach a non-Turkish user in their own language.
 *
 * <p>Deliberately narrow: it translates ONLY free-form wording. It must never touch the structured
 * product data the frontend renders field-by-field (hotel/flight cards) — that is why the
 * orchestrator hands it just the {@code reply}/option strings, never the cards. The translation
 * prompt further pins numbers, prices, dates, currency/board codes and proper nouns so a price or a
 * hotel name is carried through verbatim (project guardrail: never alter or invent product data).
 *
 * <p>Turkish is the product's primary market and the source language of every template, so a
 * Turkish, blank or unknown target is a no-op with no model call — the common path costs nothing.
 * Any failure falls back to the original Turkish text rather than failing the turn: a localized
 * reply is a nicety, never a reason to drop the answer.
 */
@Service
public class ReplyLocalizer {

    private static final Logger log = LoggerFactory.getLogger(ReplyLocalizer.class);

    /** Source language of every template — translating into it would be a wasted round-trip. */
    static final String SOURCE_LANGUAGE = "tr";

    private static final String TRANSLATE_SYSTEM_PROMPT = """
        You are a translation function. Translate the user's text into the language given by the ISO
        639-1 code {{LANG}}, and output ONLY the translation — no quotes, no notes, no preamble.
        Preserve EXACTLY, without translating or reformatting: numbers, prices, dates, times, currency
        codes/symbols, star counts, airport/IATA and board-type codes (AI, HB, BB, RO), and proper
        nouns (hotel, airline, city and person names). Keep the meaning, tone and any question marks;
        do not add or remove information. Return the text unchanged if it is already in {{LANG}}.
        """;

    private final ChatClient chatClient;

    public ReplyLocalizer(ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    /**
     * @return true when {@code targetLanguage} names a real language other than the Turkish source,
     *         i.e. when a translation is actually needed. Case-insensitive; null/blank ⇒ false.
     */
    public boolean shouldLocalize(@Nullable String targetLanguage) {
        return targetLanguage != null
                && !targetLanguage.isBlank()
                && !targetLanguage.trim().equalsIgnoreCase(SOURCE_LANGUAGE);
    }

    /**
     * Translates {@code text} into {@code targetLanguage}, or returns it unchanged when no
     * translation is needed (Turkish/blank/unknown target, or empty text) or when the model call
     * fails. Never throws.
     */
    public String localize(@Nullable String text, @Nullable String targetLanguage) {
        if (text == null || text.isBlank() || !shouldLocalize(targetLanguage)) {
            return text;
        }
        String lang = targetLanguage.trim().toLowerCase();
        try {
            String translated = chatClient.prompt()
                    .system(TRANSLATE_SYSTEM_PROMPT.replace("{{LANG}}", lang))
                    .user(text)
                    .call()
                    .content();
            return (translated == null || translated.isBlank()) ? text : translated.trim();
        } catch (RuntimeException e) {
            // Fail-safe: a failed translation must not swallow the answer — show the source text.
            log.warn("Reply localization to '{}' failed, returning source text: {}", lang, e.getMessage());
            return text;
        }
    }
}
