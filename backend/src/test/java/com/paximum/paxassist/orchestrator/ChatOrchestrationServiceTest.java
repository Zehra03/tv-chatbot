package com.paximum.paxassist.orchestrator;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.paximum.paxassist.ai.IntentExtractionResult;
import com.paximum.paxassist.ai.IntentExtractionService;
import com.paximum.paxassist.ai.IntentType;
import com.paximum.paxassist.chat.domain.ChatSession;
import com.paximum.paxassist.chat.service.ChatSessionStore;
import com.paximum.paxassist.guard.GuardBlockedException;
import com.paximum.paxassist.guard.GuardAuditLogger;
import com.paximum.paxassist.guard.GuardOrchestrator;
import com.paximum.paxassist.orchestrator.intent.IntentHandler;
import com.paximum.paxassist.orchestrator.intent.IntentRouter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatOrchestrationServiceTest {

    @Mock
    private GuardOrchestrator guard;
    @Mock
    private GuardAuditLogger guardAuditLogger;
    @Mock
    private IntentExtractionService intentExtraction;
    @Mock
    private ChatSessionStore sessionStore;
    @Mock
    private IntentRouter intentRouter;
    @Mock
    private IntentHandler handler;

    private ChatOrchestrationService service() {
        return new ChatOrchestrationService(guard, guardAuditLogger, intentExtraction, sessionStore, intentRouter);
    }

    @Test
    void guardBlock_auditsAndReturnsSafeMessage_withoutCallingAiOrRouter() {
        when(sessionStore.getOrCreate(any(), any())).thenReturn(new ChatSession("s1"));
        when(guard.processInput("zararlı"))
                .thenThrow(new GuardBlockedException("Güvenlik politikaları gereği reddedildi.", "Prompt Injection"));

        OrchestrationResult result = service().handle(null, "zararlı", 7L).result();

        assertThat(result.reply()).isEqualTo("Güvenlik politikaları gereği reddedildi.");
        assertThat(result.sessionId()).isEqualTo("s1");
        verify(guardAuditLogger).logBlockedRequestAsync("zararlı", "Prompt Injection");
        verifyNoInteractions(intentExtraction);
        verify(intentRouter, never()).route(any());
    }

    @Test
    void happyPath_extractsRoutesPersistsAndStampsSessionId() {
        ChatSession session = new ChatSession("s1");
        when(sessionStore.getOrCreate(any(), any())).thenReturn(session);
        when(intentExtraction.extract(eq("merhaba"), anyList()))
                .thenReturn(new IntentExtractionResult(IntentType.OTHER, null));
        when(intentRouter.route(IntentType.OTHER)).thenReturn(handler);
        when(handler.handle(any())).thenReturn(OrchestrationResult.message("Size nasıl yardımcı olabilirim?"));

        OrchestrationOutcome outcome = service().handle("s1", "merhaba", 7L);

        assertThat(outcome.result().reply()).isEqualTo("Size nasıl yardımcı olabilirim?");
        assertThat(outcome.result().sessionId()).isEqualTo("s1");
        assertThat(outcome.session()).isSameAs(session);
        // user + assistant turns are appended to the session transcript
        assertThat(session.getMessages()).hasSize(2);
        verify(sessionStore).save(session);
        // OTHER turn is registered for out-of-scope tracking
        verify(guard).registerOutOfScope(7L, true);
    }

    @Test
    void blockedUser_shortCircuitsBeforeExtraction() {
        when(sessionStore.getOrCreate(any(), any())).thenReturn(new ChatSession("s1"));
        doThrow(new GuardBlockedException("Çok fazla konu dışı istek gönderdiniz. Lütfen bir süre sonra tekrar deneyin.",
                "Out-of-scope abuse: user temporarily blocked"))
                .when(guard).assertNotBlocked(7L);

        OrchestrationResult result = service().handle("s1", "merhaba", 7L).result();

        assertThat(result.reply())
                .isEqualTo("Çok fazla konu dışı istek gönderdiniz. Lütfen bir süre sonra tekrar deneyin.");
        verify(guardAuditLogger).logBlockedRequestAsync("merhaba", "Out-of-scope abuse: user temporarily blocked");
        verifyNoInteractions(intentExtraction);
        verify(intentRouter, never()).route(any());
        verify(sessionStore, never()).save(any());
    }

    @Test
    void outOfScopeThresholdReached_blocksThisTurn_withoutRoutingOrPersisting() {
        when(sessionStore.getOrCreate(any(), any())).thenReturn(new ChatSession("s1"));
        when(intentExtraction.extract(eq("nasılsın"), anyList()))
                .thenReturn(new IntentExtractionResult(IntentType.OTHER, null));
        doThrow(new GuardBlockedException("Çok fazla konu dışı istek gönderdiniz. Lütfen bir süre sonra tekrar deneyin.",
                "Out-of-scope abuse: user temporarily blocked"))
                .when(guard).registerOutOfScope(7L, true);

        OrchestrationResult result = service().handle("s1", "nasılsın", 7L).result();

        assertThat(result.reply())
                .isEqualTo("Çok fazla konu dışı istek gönderdiniz. Lütfen bir süre sonra tekrar deneyin.");
        verify(guardAuditLogger).logBlockedRequestAsync("nasılsın", "Out-of-scope abuse: user temporarily blocked");
        verify(intentRouter, never()).route(any());
        verify(sessionStore, never()).save(any());
    }

    @Test
    void inScopeTurn_registersAsNonOutOfScope_resettingStreak() {
        ChatSession session = new ChatSession("s1");
        when(sessionStore.getOrCreate(any(), any())).thenReturn(session);
        when(intentExtraction.extract(eq("Antalya'da otel"), anyList()))
                .thenReturn(new IntentExtractionResult(IntentType.HOTEL, null));
        when(intentRouter.route(IntentType.HOTEL)).thenReturn(handler);
        when(handler.handle(any())).thenReturn(OrchestrationResult.message("8 otel buldum"));

        service().handle("s1", "Antalya'da otel", 7L);

        verify(guard).registerOutOfScope(7L, false);
        verify(sessionStore).save(session);
    }
}
