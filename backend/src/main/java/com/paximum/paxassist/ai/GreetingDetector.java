package com.paximum.paxassist.ai;

import java.util.Locale;
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

    public boolean isPureGreeting(String message) {
        if (message == null || message.isBlank() || message.length() > MAX_LENGTH) {
            return false;
        }
        String normalized = NOT_LETTER_OR_SPACE.matcher(message.toLowerCase(TURKISH))
                .replaceAll(" ")
                .replaceAll("\\s+", " ")
                .strip();
        return !normalized.isEmpty() && PURE_GREETING.matcher(normalized).matches();
    }
}
