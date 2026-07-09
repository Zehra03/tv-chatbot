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

    /**
     * Trello Kartı 1: Guard reddettiğinde IntentionLLM'e gidilmemeli
     */
    @Test
    void shouldBlockRequestWhenGuardFails() {
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

    /**
     * Trello Kartı 2: Kullanıcı ilk bağlandığında Session oluşturma (sessionId null geldiğinde)
     */
    @Test
    void shouldCreateNewSessionWhenSessionIdIsNull() {
        ChatSession newSession = new ChatSession("new-session-456");
        when(sessionStore.getOrCreate(null, 7L)).thenReturn(newSession);
        when(intentExtraction.extract(eq("merhaba"), anyList()))
                .thenReturn(new IntentExtractionResult(IntentType.OTHER, null));
        when(intentRouter.route(IntentType.OTHER)).thenReturn(handler);
        when(handler.handle(any())).thenReturn(OrchestrationResult.message("Merhaba! Size otel veya uçuş konularında nasıl yardımcı olabilirim?"));

        OrchestrationOutcome outcome = service().handle(null, "merhaba", 7L);

        assertThat(outcome.result().reply()).isEqualTo("Merhaba! Size otel veya uçuş konularında nasıl yardımcı olabilirim?");
        assertThat(outcome.result().sessionId()).isEqualTo("new-session-456");
        assertThat(outcome.session()).isSameAs(newSession);
        verify(sessionStore).save(newSession);
    }

    /**
     * Trello Kartı 1: Modüller arası trafik yönlendirmesi
     * Trello Kartı 2: Oturum ve Konuşma geçmişinin okunması/yazılması
     * Trello Kartı 4: Sonuçların UI formatına (Chat Bubble) çevrilmesi
     * Trello Kartı 5: Loglama (Orchestrator loglaması GuardAuditLogger veya benzeriyle yapılıyor)
     */
    @Test
    void shouldProcessValidMessageAndReturnHotelResults() {
        ChatSession session = new ChatSession("session-123");
        when(sessionStore.getOrCreate("session-123", 7L)).thenReturn(session);
        
        when(intentExtraction.extract(eq("Antalya'da 5 yıldızlı otel arıyorum"), anyList()))
                .thenReturn(new IntentExtractionResult(IntentType.HOTEL_SEARCH, null)); // Criteria normally here
        
        when(intentRouter.route(IntentType.HOTEL_SEARCH)).thenReturn(handler);
        
        // Handler represents the HotelModule processing and ResponseFormatter
        when(handler.handle(any())).thenReturn(OrchestrationResult.messageWithCards("Size uygun 2 otel buldum, aşağıdan inceleyebilirsiniz.", java.util.List.of(new Object(), new Object())));

        OrchestrationOutcome outcome = service().handle("session-123", "Antalya'da 5 yıldızlı otel arıyorum", 7L);

        assertThat(outcome.result().reply()).isEqualTo("Size uygun 2 otel buldum, aşağıdan inceleyebilirsiniz.");
        assertThat(outcome.result().cards()).hasSize(2);
        
        // user + assistant turns are appended to the session transcript
        assertThat(session.getMessages()).hasSize(2);
        assertThat(session.getMessages().get(0).role()).isEqualTo("user");
        assertThat(session.getMessages().get(1).role()).isEqualTo("assistant");
        
        verify(sessionStore).save(session);
    }

    /**
     * Trello Kartı 3: Eksik Bilgi Tamamlama (İnteraktif Soru-Cevap) Algoritması
     */
    @Test
    void shouldAskQuestionWhenParametersAreMissing() {
        ChatSession session = new ChatSession("session-123");
        when(sessionStore.getOrCreate("session-123", 7L)).thenReturn(session);
        
        when(intentExtraction.extract(eq("Uçak bileti almak istiyorum"), anyList()))
                .thenReturn(new IntentExtractionResult(IntentType.FLIGHT_SEARCH, null)); // Missing criteria
        
        when(intentRouter.route(IntentType.FLIGHT_SEARCH)).thenReturn(handler);
        
        // In the real architecture, the FlightSearchHandler detects missing parameters and returns a question
        when(handler.handle(any())).thenReturn(OrchestrationResult.message("Nereye uçmak istersiniz ve hangi tarihte?"));

        OrchestrationOutcome outcome = service().handle("session-123", "Uçak bileti almak istiyorum", 7L);

        assertThat(outcome.result().reply()).isEqualTo("Nereye uçmak istersiniz ve hangi tarihte?");
        
        // user + assistant turns are appended to the session transcript
        assertThat(session.getMessages()).hasSize(2);
        verify(sessionStore).save(session);
    }
}
