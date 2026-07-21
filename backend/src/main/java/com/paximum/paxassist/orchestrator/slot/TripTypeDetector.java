package com.paximum.paxassist.orchestrator.slot;

import java.util.Locale;
import java.util.Optional;

import org.springframework.stereotype.Component;

/**
 * Deterministic "gidiş-dönüş" / "tek yön" detector for the chat flight flow.
 *
 * <p>The trip type decides whether a return date is REQUIRED
 * ({@code FlightSearchCriteria#missingRequiredFields}), so getting it wrong silently changes what the
 * user is shown: "gidiş-dönüş uçuş arıyorum" used to reach the search with no trip type at all, the
 * mapper inferred ONE_WAY from the absent return date, and the user got one-way results for a
 * round-trip request instead of being asked for the return date.
 *
 * <p>Why not leave it to the extraction model: this phrase is the whole request, it is stated in a
 * handful of fixed forms, and a missed one costs a wrong result list. The model may still fill
 * {@code tripType} — this only adds a floor under it (like {@link SlotGuard} for dates).
 */
@Component
public class TripTypeDetector {

    /**
     * Round-trip wordings. Matched on a text whose dashes/slashes have been folded to spaces, so
     * "gidiş-dönüş", "gidiş dönüş" and "gidiş/dönüş" are one phrase here.
     */
    private static final String[] ROUND_TRIP_PHRASES = {
            "gidis donus", "gidisdonus", "gidis ve donus", "donus bileti de", "donusu de",
            "round trip", "roundtrip", "cift yon", "cift yonlu",
    };

    /** One-way wordings — the user narrowing an earlier round-trip request back down. */
    private static final String[] ONE_WAY_PHRASES = {
            "tek yon", "tek gidis", "sadece gidis", "yalnizca gidis", "one way", "oneway",
            "donus istemiyorum", "donmeyecegim",
    };

    /**
     * @return the trip type the message states, or empty when it states none (the usual case — most
     *         turns say nothing about the direction and must not overwrite what was said before).
     */
    public Optional<String> detect(String userMessage) {
        if (userMessage == null || userMessage.isBlank()) {
            return Optional.empty();
        }
        String text = fold(userMessage);
        // One-way is checked first: "gidiş dönüş değil, tek yön" corrects an earlier round trip, and
        // the correction is what the user means.
        for (String phrase : ONE_WAY_PHRASES) {
            if (text.contains(phrase)) {
                return Optional.of(SlotNormalizer.TRIP_TYPE_ONE_WAY);
            }
        }
        for (String phrase : ROUND_TRIP_PHRASES) {
            if (text.contains(phrase)) {
                return Optional.of(SlotNormalizer.TRIP_TYPE_ROUND);
            }
        }
        return Optional.empty();
    }

    /**
     * Lower-cases and strips Turkish diacritics so one spelling of each phrase covers what users
     * actually type ("GIDIŞ-DÖNÜŞ", "gidis donus", "Gidiş Dönüş"). Dashes, slashes and repeated
     * spaces collapse to a single space.
     */
    private static String fold(String value) {
        String lower = value.toLowerCase(Locale.forLanguageTag("tr"));
        StringBuilder folded = new StringBuilder(lower.length());
        for (char c : lower.toCharArray()) {
            switch (c) {
                case 'ı', 'i', 'î' -> folded.append('i');
                case 'ş' -> folded.append('s');
                case 'ğ' -> folded.append('g');
                case 'ü', 'û' -> folded.append('u');
                case 'ö' -> folded.append('o');
                case 'ç' -> folded.append('c');
                case '-', '/', '_', ',', '.', '!', '?' -> folded.append(' ');
                default -> folded.append(c);
            }
        }
        return folded.toString().replaceAll("\\s+", " ").trim();
    }
}
