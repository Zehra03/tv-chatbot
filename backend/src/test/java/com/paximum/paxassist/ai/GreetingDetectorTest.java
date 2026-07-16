package com.paximum.paxassist.ai;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class GreetingDetectorTest {

    private final GreetingDetector detector = new GreetingDetector();

    @ParameterizedTest
    @ValueSource(strings = {
            "merhaba", "Merhaba", "MERHABA", "selam", "slm", "mrb", "merhabalar", "selamlar",
            "günaydın", "iyi günler", "iyi akşamlar", "hey", "hello",
            "merhaba!", "selam!!!", "  merhaba  ", "merhaba 👋", "selam paxi", "merhaba merhaba"
    })
    void recognisesABareGreeting(String message) {
        assertThat(detector.isPureGreeting(message)).isTrue();
    }

    // The whole point of the all-or-nothing match: a greeting glued to a real request must be
    // answered as a search, not greeted.
    @ParameterizedTest
    @ValueSource(strings = {
            "merhaba, uçak arıyorum", "selam Antalya'da otel bakıyorum", "merhaba nasılsın",
            "iyi günler İstanbul'a uçuş var mı", "uçak istiyorum"
    })
    void doesNotRecogniseAGreetingCarryingARequest(String message) {
        assertThat(detector.isPureGreeting(message)).isFalse();
    }

    @Test
    void doesNotTreatABareIyiAsAGreeting() {
        // "iyi" alone answers "nasılsın?" — only a time of day makes it a greeting.
        assertThat(detector.isPureGreeting("iyi")).isFalse();
        assertThat(detector.isPureGreeting("iyi günler")).isTrue();
    }

    @Test
    void ignoresBlankAndOverlongInput() {
        assertThat(detector.isPureGreeting(null)).isFalse();
        assertThat(detector.isPureGreeting("   ")).isFalse();
        assertThat(detector.isPureGreeting("merhaba".repeat(20))).isFalse();
    }

    @Test
    void doesNotRecognisePunctuationOnly() {
        assertThat(detector.isPureGreeting("!!!")).isFalse();
    }
}
