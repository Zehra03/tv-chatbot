package com.paximum.paxassist.chat.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;

/**
 * Covers the deterministic guard of {@link ReplyLocalizer}: Turkish is the source language and
 * blank/unknown targets need no translation, so those paths must return the text untouched WITHOUT
 * ever calling the model. The actual translation path needs a live model and is not unit-tested.
 */
@ExtendWith(MockitoExtension.class)
class ReplyLocalizerTest {

    @Mock
    private ChatClient chatClient;

    private ReplyLocalizer localizer() {
        return new ReplyLocalizer(chatClient);
    }

    @Test
    void shouldLocalizeIsTrueOnlyForANonTurkishLanguage() {
        ReplyLocalizer localizer = localizer();

        assertThat(localizer.shouldLocalize("en")).isTrue();
        assertThat(localizer.shouldLocalize("de")).isTrue();
        assertThat(localizer.shouldLocalize("tr")).isFalse();
        assertThat(localizer.shouldLocalize("TR")).isFalse();   // case-insensitive
        assertThat(localizer.shouldLocalize(null)).isFalse();
        assertThat(localizer.shouldLocalize("  ")).isFalse();
    }

    @Test
    void turkishTargetIsANoOpWithoutCallingTheModel() {
        String text = "Aramana uygun 2 otel buldum:";

        assertThat(localizer().localize(text, "tr")).isEqualTo(text);
        verifyNoInteractions(chatClient);
    }

    @Test
    void nullOrBlankTargetIsANoOpWithoutCallingTheModel() {
        String text = "Nereden kalkmak istersin?";

        assertThat(localizer().localize(text, null)).isEqualTo(text);
        assertThat(localizer().localize(text, "")).isEqualTo(text);
        verifyNoInteractions(chatClient);
    }

    @Test
    void blankTextIsReturnedAsIsWithoutCallingTheModel() {
        assertThat(localizer().localize(null, "en")).isNull();
        assertThat(localizer().localize("   ", "en")).isEqualTo("   ");
        verifyNoInteractions(chatClient);
    }
}
