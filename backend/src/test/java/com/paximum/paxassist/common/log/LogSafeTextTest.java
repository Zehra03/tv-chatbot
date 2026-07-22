package com.paximum.paxassist.common.log;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * The PII rule for logs is enforced here rather than trusted to callers, so these cases are the rule
 * itself — not incidental behaviour.
 */
class LogSafeTextTest {

    @Test
    void masksACardNumberEvenWhenACallerPassesOneByMistake() {
        String scrubbed = LogSafeText.scrub("payment failed for 4532 1234 5678 9012");

        assertThat(scrubbed).doesNotContain("4532").doesNotContain("9012");
        assertThat(scrubbed).contains("[CARD]");
    }

    @Test
    void masksANationalIdAndAnIban() {
        assertThat(LogSafeText.scrub("tckn 12345678901")).contains("[ID]").doesNotContain("12345678901");
        assertThat(LogSafeText.scrub("iban TR330006100519786457841326"))
                .contains("[IBAN]").doesNotContain("TR330006100519786457841326");
    }

    @Test
    void stripsNewlinesSoAValueCannotForgeASecondLogLine() {
        // With JSON lines a forged line would parse as a genuine event, not read as garbage.
        String scrubbed = LogSafeText.scrub("destination=Antalya\n{\"message\":\"admin logged in\"}");

        assertThat(scrubbed).doesNotContain("\n").doesNotContain("\r");
    }

    @Test
    void boundsTheLength_soOneEventCannotFloodTheStream() {
        String scrubbed = LogSafeText.scrub("x".repeat(LogSafeText.MAX_LENGTH + 500));

        assertThat(scrubbed).hasSizeLessThan(LogSafeText.MAX_LENGTH + 50);
        assertThat(scrubbed).endsWith("[truncated]");
    }

    @Test
    void leavesOrdinaryOperationalTextAlone() {
        // Masking must catch a leak, not rewrite the summaries the log is actually made of.
        String summary = "products=HF amount=1500.00 EUR travellers=2 checkIn=2026-09-10";

        assertThat(LogSafeText.scrub(summary)).isEqualTo(summary);
    }

    @Test
    void nullIsPassedThrough() {
        assertThat(LogSafeText.scrub(null)).isNull();
    }
}
