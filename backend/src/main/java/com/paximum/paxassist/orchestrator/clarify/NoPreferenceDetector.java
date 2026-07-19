package com.paximum.paxassist.orchestrator.clarify;

import java.text.Normalizer;
import java.util.List;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

/**
 * Detects that the user has no preference for the slot they were just asked about — "fark etmez",
 * "sen seç", "önemli değil".
 *
 * <p>Such an answer fills no slot, so without this the turn re-runs the same incomplete search and
 * the user is asked the very same question again. Detection is deterministic (regex on the raw
 * message) rather than left to the LLM: it is the same reasoning as {@link ClarificationCatalog} —
 * no model call is needed to recognise a fixed set of phrases, and a small model cannot be trusted
 * to do it consistently.
 *
 * <p>Matching is accent- and case-insensitive over the Turkish text ("fark etmez" / "farketmez" /
 * "FARK ETMEZ"), and requires only that the phrase appear in the message, since these answers are
 * typically the whole message ("fark etmez") or a short clause ("benim için fark etmez").
 */
@Component
public class NoPreferenceDetector {

    // Deliberately narrow: each phrase must mean "you decide / any is fine". Vaguer answers
    // ("bilmiyorum", "boş ver") are NOT here — they may mean the user wants to abandon the search,
    // and treating them as consent to a suggestion would put words in their mouth.
    private static final List<Pattern> NO_PREFERENCE = List.of(
            Pattern.compile("fark\\s*etmez"),
            Pattern.compile("farketmez"),
            Pattern.compile("farki\\s*yok"),
            Pattern.compile("onemli\\s*degil"),
            Pattern.compile("sen\\s*(sec|karar\\s*ver|bil|belirle)"),
            Pattern.compile("sana\\s*kalmis"),
            Pattern.compile("(nere|hangi)si\\s*olursa\\s*olsun"),
            Pattern.compile("ne\\s*olursa\\s*olsun"),
            Pattern.compile("herhangi\\s*bir[i]?"),
            Pattern.compile("hepsi\\s*(olur|uyar)"),
            Pattern.compile("(farketmiyor|fark\\s*etmiyor)")
    );

    public boolean isNoPreference(String userMessage) {
        if (userMessage == null || userMessage.isBlank()) {
            return false;
        }
        String normalized = normalize(userMessage);
        return NO_PREFERENCE.stream().anyMatch(pattern -> pattern.matcher(normalized).find());
    }

    /**
     * Lowercases and strips Turkish diacritics so one pattern covers "seç"/"sec" and "değil"/"degil".
     * ı/İ are folded first: {@code toLowerCase} with the default locale would otherwise turn "I" into
     * a dotless "ı" (or not) depending on where the server runs.
     */
    private String normalize(String text) {
        String folded = text.replace('İ', 'i').replace('I', 'i').replace('ı', 'i')
                .toLowerCase(java.util.Locale.ROOT);
        return Normalizer.normalize(folded, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
    }
}
