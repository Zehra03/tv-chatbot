package com.paximum.paxassist.guard;

import java.util.Locale;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

@Component
public class GuardRuleService {

    private static final Locale TURKISH = Locale.forLanguageTag("tr");

    public GuardResult evaluate(String input) {
        if (input == null || input.isBlank()) {
            return new GuardResult(false, null);
        }

        if (GuardPatterns.IBAN.matcher(input).find()) {
            return new GuardResult(true, "KVKK - Sensitive Data (IBAN) Detected");
        }
        if (GuardPatterns.CREDIT_CARD.matcher(input).find()) {
            return new GuardResult(true, "KVKK - Sensitive Data (Credit Card) Detected");
        }
        if (GuardPatterns.TCKN.matcher(input).find()) {
            return new GuardResult(true, "KVKK - Sensitive Data (TCKN) Detected");
        }

        // Locale.ROOT lowercases the Turkish dotless "I" to "i" instead of "ı" (and "İ" to a
        // two-char "i̇"), so Turkish phrases like "ARTIK" fail to match "artık" unless we also
        // lowercase with the Turkish locale, which applies the correct dotted/dotless mapping.
        String lowerCaseInputRoot = input.toLowerCase(Locale.ROOT);
        String lowerCaseInputTr = input.toLowerCase(TURKISH);
        for (String phrase : GuardPatterns.PROMPT_INJECTION_PHRASES) {
            if (lowerCaseInputRoot.contains(phrase) || lowerCaseInputTr.contains(phrase)) {
                return new GuardResult(true, "Prompt Injection Detected");
            }
        }
        for (Pattern profanityPattern : GuardPatterns.PROFANITY_PATTERNS) {
            if (profanityPattern.matcher(input).find()) {
                return new GuardResult(true, "Profanity Detected");
            }
        }

        return new GuardResult(false, null);
    }
}
