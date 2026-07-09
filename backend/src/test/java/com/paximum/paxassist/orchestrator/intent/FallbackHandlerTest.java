package com.paximum.paxassist.orchestrator.intent;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.paximum.paxassist.ai.IntentType;
import com.paximum.paxassist.chat.domain.ChatSession;
import com.paximum.paxassist.chat.service.ChatService;
import com.paximum.paxassist.orchestrator.OrchestrationContext;
import com.paximum.paxassist.orchestrator.OrchestrationResult;
import com.paximum.paxassist.orchestrator.refine.Evaluator;
import com.paximum.paxassist.orchestrator.refine.EvaluatorOptimizer;
import com.paximum.paxassist.orchestrator.refine.Generator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FallbackHandlerTest {

    @Mock
    private ChatService chatService;
    @Mock
    private EvaluatorOptimizer evaluatorOptimizer;
    @Mock
    private Evaluator evaluator;

    @Test
    void supportsOnlyOtherIntent() {
        FallbackHandler handler = new FallbackHandler(chatService, evaluatorOptimizer, evaluator);
        assertThat(handler.supports(IntentType.OTHER)).isTrue();
        assertThat(handler.supports(IntentType.HOTEL)).isFalse();
    }

    @Test
    void returnsRefinedReplyFromTheEvaluatorLoop() {
        FallbackHandler handler = new FallbackHandler(chatService, evaluatorOptimizer, evaluator);
        when(evaluatorOptimizer.refine(any(Generator.class), eq(evaluator), eq("merhaba"), any(), any()))
                .thenReturn("Size otel veya uçuş aramasında yardımcı olabilirim.");

        OrchestrationResult result = handler.handle(
                new OrchestrationContext(new ChatSession("s1"), "merhaba", IntentType.OTHER, null));

        assertThat(result.reply()).isEqualTo("Size otel veya uçuş aramasında yardımcı olabilirim.");
        assertThat(result.redirectToReservation()).isFalse();
        assertThat(result.cards()).isEmpty();
    }
}
