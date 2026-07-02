package com.paximum.paxassist.guard;

import java.util.Locale;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

@Component
public class GuardRuleService {

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

        String lowerCaseInput = input.toLowerCase(Locale.ROOT);
        for (String phrase : GuardPatterns.PROMPT_INJECTION_PHRASES) {
            if (lowerCaseInput.contains(phrase)) {
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
