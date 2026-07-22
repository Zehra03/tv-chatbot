package com.paximum.paxassist.ai;

import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

/**
 * Recognises a message that is <b>nothing but</b> a greeting ("merhaba", "selam Paxi", "iyi günler"),
 * so the turn can be answered with a fixed sentence instead of being sent to the model.
 *
 * <p>The match is deliberately all-or-nothing: every word must be a greeting word. A message that
 * merely <em>starts</em> with one but carries a request ("merhaba, uçak arıyorum") is not a greeting
 * — it goes down the normal intent pipeline and is answered as a search, never greeted.
 *
 * <p>Anything this does not recognise is not rejected, only handed to the model as usual, so an
 * unusual greeting still gets a sensible (just not verbatim-guaranteed) reply.
 */
@Component
public class GreetingDetector {

    /** Beyond this a message is not a bare greeting; also keeps the regex off unbounded input. */
    private static final int MAX_LENGTH = 64;

    private static final Locale TURKISH = Locale.forLanguageTag("tr");

    /**
     * One greeting word/phrase. "iyi" only counts glued to a time of day, so a bare "iyi" (an answer
     * to "nasılsın?", not a greeting) is not treated as one. {@code h[iı]} covers Turkish lowercasing
     * turning an uppercase "HI" into "hı".
     */
    private static final String GREETING_WORD =
            "merhaba(lar)?|selam(lar)?|slm|mrb|günaydın|tünaydın|hey|h[iı]|hello|paxi"
                    + "|iyi (günler|akşamlar|sabahlar|geceler)";

    /** The whole message must be greeting words, optionally repeated ("selam merhaba", "merhaba paxi"). */
    private static final Pattern PURE_GREETING =
            Pattern.compile("^(" + GREETING_WORD + ")( (" + GREETING_WORD + "))*$");

    /** Punctuation and emoji are decoration on a greeting ("selam!!", "merhaba 👋"), not content. */
    private static final Pattern NOT_LETTER_OR_SPACE = Pattern.compile("[^\\p{L} ]");

    /**
     * Greeting words that are unambiguously English. "hı" is the Turkish lowercasing of "HI". "hey"
     * is deliberately excluded — it is just as common in Turkish chat, so it stays language-neutral
     * and falls back to the default rather than flipping a Turkish user to English.
     */
    private static final Set<String> ENGLISH_GREETING_WORDS = Set.of("hi", "hı", "hello");

    /** Language-neutral greeting tokens that reveal nothing about the language on their own. */
    private static final Set<String> NEUTRAL_GREETING_WORDS = Set.of("paxi", "hey");

    public boolean isPureGreeting(String message) {
        if (message == null || message.isBlank() || message.length() > MAX_LENGTH) {
            return false;
        }
        String normalized = normalize(message);
        return !normalized.isEmpty() && PURE_GREETING.matcher(normalized).matches();
    }

    /**
     * The ISO 639-1 language of a bare greeting, told from the greeting words alone, so the fixed
     * greeting reply can be produced in that language. Only the words this detector recognises are
     * classified: English words ("hi", "hello") give {@code "en"}; anything else it recognises is a
     * Turkish (or Turkish-glued) greeting and gives {@code "tr"}. A purely neutral greeting
     * ("paxi", "hey") is {@link Optional#empty()} — the caller then keeps the default language.
     *
     * <p>Other languages (German "hallo", Russian "привет", Arabic "مرحبا") are intentionally NOT
     * greeting-matched here, so they flow through the model + reply-localizer path and are answered
     * in their own language that way; this method only resolves the words the short-circuit catches.
     *
     * @return the greeting's language, or empty when it is not a pure greeting or is language-neutral
     */
    public Optional<String> greetingLanguage(String message) {
        if (!isPureGreeting(message)) {
            return Optional.empty();
        }
        boolean anyEnglish = false;
        for (String word : normalize(message).split(" ")) {
            if (ENGLISH_GREETING_WORDS.contains(word)) {
                anyEnglish = true;
            } else if (!NEUTRAL_GREETING_WORDS.contains(word)) {
                // Any other recognised greeting word is Turkish-specific (merhaba, selam, günaydın,
                // "iyi günler"…); Turkish wins so a mixed "hi merhaba" is treated as Turkish.
                return Optional.of("tr");
            }
        }
        return anyEnglish ? Optional.of("en") : Optional.empty();
    }

    private String normalize(String message) {
        return NOT_LETTER_OR_SPACE.matcher(message.toLowerCase(TURKISH))
                .replaceAll(" ")
                .replaceAll("\\s+", " ")
                .strip();
    }
}
