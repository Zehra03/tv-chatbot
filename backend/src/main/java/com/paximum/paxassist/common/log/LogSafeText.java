package com.paximum.paxassist.common.log;

import java.util.regex.Pattern;

/**
 * Last-line defence applied to every value {@link ActivityLog} writes.
 *
 * <p>Callers are already expected to pass PII-safe summaries, but "expected to" is a convention, and
 * a convention is one careless edit away from a customer's card number sitting in a log aggregator
 * that has none of the database's access controls. This makes the rule enforced instead:
 *
 * <ul>
 *   <li><b>CR/LF stripped</b> — the important one now that log lines are JSON. A crafted value
 *       containing a newline could otherwise close the line and inject a second one that a collector
 *       would parse as a genuine event, letting an attacker forge log records.</li>
 *   <li><b>Card / national-id / IBAN shapes masked</b> — the same three KVKK-sensitive shapes the
 *       guard module masks out of blocked input, applied here to everything else.</li>
 *   <li><b>Length bounded</b> — an unbounded value (a whole request body, a stack of results) turns
 *       one event into kilobytes and pushes the rest of the stream out of any retention window.</li>
 * </ul>
 *
 * <p>The patterns are deliberately narrow: masking is meant to catch a leak, not to rewrite ordinary
 * text. They are duplicated from — rather than shared with — the guard module's masker, because that
 * one is internal to guard and reaching across a module's internals is exactly what the project's
 * boundaries forbid.
 */
final class LogSafeText {

    /** Long enough for a masked summary or a request line; short enough that one event stays one event. */
    static final int MAX_LENGTH = 512;

    private static final Pattern CRLF = Pattern.compile("[\\r\\n]");
    /** 13–19 digits, optionally grouped by spaces or dashes — card-number shaped. */
    private static final Pattern CARD = Pattern.compile("\\b(?:\\d[ -]?){13,19}\\b");
    /** Exactly 11 standalone digits — Turkish national id shaped. */
    private static final Pattern NATIONAL_ID = Pattern.compile("\\b\\d{11}\\b");
    /** TR IBAN: two letters, two check digits, then 22 more alphanumerics. */
    private static final Pattern IBAN = Pattern.compile("\\b[A-Z]{2}\\d{2}[A-Z0-9]{11,30}\\b");

    private LogSafeText() {
    }

    static String scrub(String value) {
        if (value == null) {
            return null;
        }
        String safe = CRLF.matcher(value).replaceAll("_");
        // IBAN first: it starts with letters, so running the digit patterns first would eat its tail
        // and leave a fragment that no longer looks like an IBAN to mask.
        safe = IBAN.matcher(safe).replaceAll("[IBAN]");
        safe = CARD.matcher(safe).replaceAll("[CARD]");
        safe = NATIONAL_ID.matcher(safe).replaceAll("[ID]");
        if (safe.length() > MAX_LENGTH) {
            safe = safe.substring(0, MAX_LENGTH) + "…[truncated]";
        }
        return safe;
    }
}
