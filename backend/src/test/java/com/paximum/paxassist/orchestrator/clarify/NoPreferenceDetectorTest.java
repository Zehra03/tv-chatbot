package com.paximum.paxassist.orchestrator.clarify;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class NoPreferenceDetectorTest {

    private final NoPreferenceDetector detector = new NoPreferenceDetector();

    @ParameterizedTest
    @ValueSource(strings = {
            "fark etmez",
            "farketmez",
            "Fark Etmez",
            "FARK ETMEZ",
            "benim için fark etmez",
            "farkı yok",
            "önemli değil",
            "onemli degil",
            "sen seç",
            "sen sec",
            "sen karar ver",
            "sen bil",
            "sana kalmış",
            "hangisi olursa olsun",
            "ne olursa olsun",
            "herhangi biri",
            "fark etmiyor"
    })
    void recognisesNoPreferencePhrases(String message) {
        assertThat(detector.isNoPreference(message)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "Ankara",
            "İstanbul'dan kalkmak istiyorum",
            "2 kişi",
            "yarın",
            // Vague, but not consent to a suggestion — the user may be abandoning the search.
            "bilmiyorum",
            "boş ver"
    })
    void doesNotMatchRealAnswers(String message) {
        assertThat(detector.isNoPreference(message)).isFalse();
    }

    @Test
    void nullAndBlankAreNotNoPreference() {
        assertThat(detector.isNoPreference(null)).isFalse();
        assertThat(detector.isNoPreference("   ")).isFalse();
    }
}
