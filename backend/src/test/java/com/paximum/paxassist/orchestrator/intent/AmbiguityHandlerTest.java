package com.paximum.paxassist.orchestrator.intent;

import org.junit.jupiter.api.Test;

import com.paximum.paxassist.ai.IntentType;
import com.paximum.paxassist.chat.domain.ChatSession;
import com.paximum.paxassist.orchestrator.ChoiceOption;
import com.paximum.paxassist.orchestrator.OrchestrationContext;
import com.paximum.paxassist.orchestrator.OrchestrationResult;

import static org.assertj.core.api.Assertions.assertThat;

class AmbiguityHandlerTest {

    private final AmbiguityHandler handler = new AmbiguityHandler();

    private OrchestrationContext context(String userMessage) {
        return new OrchestrationContext(new ChatSession("s1"), userMessage, IntentType.AMBIGUOUS, null);
    }

    @Test
    void supportsOnlyAmbiguousIntent() {
        assertThat(handler.supports(IntentType.AMBIGUOUS)).isTrue();
        assertThat(handler.supports(IntentType.HOTEL)).isFalse();
        assertThat(handler.supports(IntentType.FLIGHT)).isFalse();
        assertThat(handler.supports(IntentType.OTHER)).isFalse();
    }

    @Test
    void handleReturnsTwoOptionDisambiguationCardCarryingTheOriginalMessage() {
        OrchestrationResult result = handler.handle(context("Antalya"));

        assertThat(result.reply()).isEqualTo(AmbiguityHandler.INTENT_QUESTION);
        assertThat(result.cards()).isEmpty();
        assertThat(result.redirectToReservation()).isFalse();
        assertThat(result.pendingQuestion()).isNull();
        assertThat(result.domain()).isNull();

        assertThat(result.options())
                .extracting(ChoiceOption::label)
                .containsExactly("Otel ara", "Uçuş ara");
        // The chosen option carries the original message so the next turn keeps the city.
        assertThat(result.options())
                .extracting(ChoiceOption::value)
                .containsExactly("Antalya için otel arıyorum", "Antalya için uçuş arıyorum");
    }

    @Test
    void handleTrimsSurroundingWhitespaceFromTheOriginalMessage() {
        OrchestrationResult result = handler.handle(context("  Bodrum  "));

        assertThat(result.options())
                .extracting(ChoiceOption::value)
                .containsExactly("Bodrum için otel arıyorum", "Bodrum için uçuş arıyorum");
    }
}
