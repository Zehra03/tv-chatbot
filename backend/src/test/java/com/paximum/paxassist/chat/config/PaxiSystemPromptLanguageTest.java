package com.paximum.paxassist.chat.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * Guards the multi-language contract of the persona prompt: Paxi must be told to reply in the
 * language of the user's latest message (defaulting to Turkish), to follow a mid-session language
 * switch, and to leave system-provided product data (prices, dates, codes) untranslated. The old
 * hard rule "yanıtların her zaman Türkçe" contradicted this and must be gone.
 */
class PaxiSystemPromptLanguageTest {

    @Test
    void guestPromptCarriesLanguageSection() {
        String prompt = PaxiSystemPrompt.forUser(null);

        assertThat(prompt).contains("## DİL");
        assertThat(prompt).contains("son mesajının dilini");
        // Follows a mid-conversation language switch instead of sticking to the previous language.
        assertThat(prompt).contains("dil değiştirirse");
        // Product data stays untranslated — only the natural-language text changes.
        assertThat(prompt).contains("ÇEVİRME");
    }

    @Test
    void promptNoLongerForcesAlwaysTurkish() {
        String prompt = PaxiSystemPrompt.forUser(null);

        assertThat(prompt).doesNotContain("her zaman Türkçe");
    }

    @Test
    void namedCallerPromptAlsoCarriesLanguageSection() {
        String prompt = PaxiSystemPrompt.forUser("Deniz");

        assertThat(prompt).contains("## DİL");
        assertThat(prompt).contains("son mesajının dilini");
    }
}
