package com.paximum.paxassist.orchestrator;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.paximum.paxassist.ai.IntentExtractionResult;
import com.paximum.paxassist.ai.IntentExtractionService;
import com.paximum.paxassist.ai.IntentType;
import com.paximum.paxassist.chat.domain.ChatCaller;
import com.paximum.paxassist.chat.domain.ChatSession;
import com.paximum.paxassist.chat.service.ChatSessionStore;
import com.paximum.paxassist.chat.service.ReplyLocalizer;
import com.paximum.paxassist.guard.GuardBlockedException;
import com.paximum.paxassist.guard.GuardAuditLogger;
import com.paximum.paxassist.guard.GuardOrchestrator;
import com.paximum.paxassist.orchestrator.intent.IntentHandler;
import com.paximum.paxassist.orchestrator.intent.IntentRouter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
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
    @Mock
    private ReplyLocalizer replyLocalizer;

    // The turn's caller. Session ownership scopes by this; the guard still keys by its raw userId (7L).
    private static final ChatCaller CALLER = ChatCaller.authenticated(7L);

    private ChatOrchestrationService service() {
        return new ChatOrchestrationService(guard, guardAuditLogger, intentExtraction, sessionStore,
                intentRouter, replyLocalizer);
    }

    /**
     * Trello Kartı 1: Guard reddettiğinde IntentionLLM'e gidilmemeli
     */
    @Test
    void shouldBlockRequestWhenGuardFails() {
        when(sessionStore.getOrCreate(any(), any())).thenReturn(new ChatSession("s1"));
        when(guard.processInput("zararlı"))
                .thenThrow(new GuardBlockedException("Güvenlik politikaları gereği reddedildi.", "Prompt Injection"));

        OrchestrationResult result = service().handle(null, "zararlı", CALLER).result();

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
        when(sessionStore.getOrCreate(null, CALLER)).thenReturn(newSession);
        when(intentExtraction.extract(eq("merhaba"), anyList()))
                .thenReturn(new IntentExtractionResult(IntentType.OTHER, null));
        when(intentRouter.route(IntentType.OTHER)).thenReturn(handler);
        when(handler.handle(any())).thenReturn(OrchestrationResult.message("Merhaba! Size otel veya uçuş konularında nasıl yardımcı olabilirim?"));

        OrchestrationOutcome outcome = service().handle(null, "merhaba", CALLER);

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
        // Test 1: Geçmiş mesajların doğru çevrildiğini test etmek için önceden mesaj ekliyoruz
        session.addMessage("user", "eski mesaj");
        session.addMessage("assistant", "eski cevap");
        
        when(sessionStore.getOrCreate("session-123", CALLER)).thenReturn(session);
        
        org.mockito.ArgumentCaptor<java.util.List<com.paximum.paxassist.ai.ChatHistoryEntry>> historyCaptor = 
                org.mockito.ArgumentCaptor.forClass(java.util.List.class);
        
        when(intentExtraction.extract(eq("Antalya'da 5 yıldızlı otel arıyorum"), historyCaptor.capture()))
                .thenReturn(new IntentExtractionResult(IntentType.HOTEL, null)); // Criteria normally here
        
        when(intentRouter.route(IntentType.HOTEL)).thenReturn(handler);
        
        org.mockito.ArgumentCaptor<OrchestrationContext> contextCaptor = 
                org.mockito.ArgumentCaptor.forClass(OrchestrationContext.class);
                
        // Handler represents the HotelModule processing and ResponseFormatter
        when(handler.handle(contextCaptor.capture())).thenReturn(OrchestrationResult.cards("Size uygun 2 otel buldum, aşağıdan inceleyebilirsiniz.", java.util.List.of(new Object(), new Object())));

        OrchestrationOutcome outcome = service().handle("session-123", "Antalya'da 5 yıldızlı otel arıyorum", CALLER);

        // Test 1 Doğrulama: Geçmiş mesajlar LLM'e (intentExtraction) doğru aktarıldı mı?
        java.util.List<com.paximum.paxassist.ai.ChatHistoryEntry> passedHistory = historyCaptor.getValue();
        assertThat(passedHistory).hasSize(2);
        assertThat(passedHistory.get(0).role()).isEqualTo("user");
        assertThat(passedHistory.get(0).content()).isEqualTo("eski mesaj");
        assertThat(passedHistory.get(1).role()).isEqualTo("assistant");
        assertThat(passedHistory.get(1).content()).isEqualTo("eski cevap");

        // Test 2 Doğrulama: OrchestrationContext doğru inşa edildi mi?
        OrchestrationContext passedContext = contextCaptor.getValue();
        assertThat(passedContext.session()).isSameAs(session);
        assertThat(passedContext.userMessage()).isEqualTo("Antalya'da 5 yıldızlı otel arıyorum");
        assertThat(passedContext.intent()).isEqualTo(IntentType.HOTEL);
        assertThat(passedContext.criteria()).isNull();

        assertThat(outcome.result().reply()).isEqualTo("Size uygun 2 otel buldum, aşağıdan inceleyebilirsiniz.");
        assertThat(outcome.result().cards()).hasSize(2);
        
        // user + assistant turns are appended to the session transcript (2 eski + 2 yeni = 4 mesaj)
        assertThat(session.getMessages()).hasSize(4);
        assertThat(session.getMessages().get(2).role()).isEqualTo("user");
        assertThat(session.getMessages().get(3).role()).isEqualTo("assistant");
        
        verify(sessionStore).save(session);
    }

    /**
     * Trello Kartı 3: Eksik Bilgi Tamamlama (İnteraktif Soru-Cevap) Algoritması
     */
    @Test
    void shouldAskQuestionWhenParametersAreMissing() {
        ChatSession session = new ChatSession("session-123");
        when(sessionStore.getOrCreate("session-123", CALLER)).thenReturn(session);
        
        when(intentExtraction.extract(eq("Uçak bileti almak istiyorum"), anyList()))
                .thenReturn(new IntentExtractionResult(IntentType.FLIGHT, null)); // Missing criteria
        
        when(intentRouter.route(IntentType.FLIGHT)).thenReturn(handler);
        
        // In the real architecture, the FlightSearchHandler detects missing parameters and returns a question
        when(handler.handle(any())).thenReturn(OrchestrationResult.message("Nereye uçmak istersiniz ve hangi tarihte?"));

        OrchestrationOutcome outcome = service().handle("session-123", "Uçak bileti almak istiyorum", CALLER);

        assertThat(outcome.result().reply()).isEqualTo("Nereye uçmak istersiniz ve hangi tarihte?");
        
        // user + assistant turns are appended to the session transcript
        assertThat(session.getMessages()).hasSize(2);
        verify(sessionStore).save(session);
        // A FLIGHT turn is in-scope, so it is registered as NOT out-of-scope (resets the streak).
        verify(guard).registerOutOfScope(7L, false);
    }

    @Test
    void blockedUser_shortCircuitsBeforeExtraction() {
        when(sessionStore.getOrCreate(any(), any())).thenReturn(new ChatSession("s1"));
        doThrow(new GuardBlockedException("Çok fazla konu dışı istek gönderdiniz. Lütfen bir süre sonra tekrar deneyin.",
                "Out-of-scope abuse: user temporarily blocked"))
                .when(guard).assertNotBlocked(7L);

        OrchestrationResult result = service().handle("s1", "merhaba", CALLER).result();

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

        OrchestrationResult result = service().handle("s1", "nasılsın", CALLER).result();

        assertThat(result.reply())
                .isEqualTo("Çok fazla konu dışı istek gönderdiniz. Lütfen bir süre sonra tekrar deneyin.");
        verify(guardAuditLogger).logBlockedRequestAsync("nasılsın", "Out-of-scope abuse: user temporarily blocked");
        verify(intentRouter, never()).route(any());
        verify(sessionStore, never()).save(any());
    }

    /**
     * A greeting must leave the consecutive-out-of-scope streak exactly as it was: counting it would
     * let "merhaba" spam earn a block, while resetting it would let a greeting slipped between
     * out-of-scope messages defeat the block.
     */
    @Test
    void greetingTurn_leavesTheOutOfScopeStreakUntouched() {
        ChatSession session = new ChatSession("s1");
        when(sessionStore.getOrCreate(any(), any())).thenReturn(session);
        when(intentExtraction.extract(eq("merhaba"), anyList()))
                .thenReturn(new IntentExtractionResult(IntentType.GREETING, null));
        when(intentRouter.route(IntentType.GREETING)).thenReturn(handler);
        when(handler.handle(any())).thenReturn(OrchestrationResult.message("Merhaba! Ben seyahat asistanın Paxi."));

        service().handle("s1", "merhaba", CALLER);

        verify(guard, never()).registerOutOfScope(any(), anyBoolean());
        verify(sessionStore).save(session);
    }

    /**
     * A non-Turkish detected language localizes the deterministic template reply (and persists the
     * localized text), while the structured cards pass through untouched.
     */
    @Test
    void nonTurkishLanguage_localizesTheDeterministicReply() {
        ChatSession session = new ChatSession("s1");
        when(sessionStore.getOrCreate(any(), any())).thenReturn(session);
        when(intentExtraction.extract(eq("a hotel in Antalya"), anyList()))
                .thenReturn(new IntentExtractionResult(IntentType.HOTEL, null, "en",
                        com.paximum.paxassist.ai.LanguageConfidence.HIGH));
        when(intentRouter.route(IntentType.HOTEL)).thenReturn(handler);
        when(handler.handle(any()))
                .thenReturn(OrchestrationResult.cards("Aramana uygun 2 otel buldum:",
                        java.util.List.of(new Object(), new Object())));
        when(replyLocalizer.shouldLocalize("en")).thenReturn(true);
        when(replyLocalizer.localize("Aramana uygun 2 otel buldum:", "en"))
                .thenReturn("I found 2 hotels matching your search:");

        OrchestrationOutcome outcome = service().handle("s1", "a hotel in Antalya", CALLER);

        assertThat(outcome.result().reply()).isEqualTo("I found 2 hotels matching your search:");
        assertThat(outcome.result().cards()).hasSize(2);
        // The persisted assistant turn keeps the localized text for later context.
        assertThat(session.getMessages().get(1).content()).isEqualTo("I found 2 hotels matching your search:");
    }

    /**
     * A clarify result carries the SAME text in {@code reply} and {@code pendingQuestion}; both must
     * come out localized, and the mirrored pending question must reuse the reply's translation rather
     * than pay for a second model round-trip.
     */
    @Test
    void clarifyResult_localizesReplyAndReusesItForTheMirroredPendingQuestion() {
        ChatSession session = new ChatSession("s1");
        when(sessionStore.getOrCreate(any(), any())).thenReturn(session);
        when(intentExtraction.extract(eq("a flight"), anyList()))
                .thenReturn(new IntentExtractionResult(IntentType.FLIGHT, null, "en",
                        com.paximum.paxassist.ai.LanguageConfidence.HIGH));
        when(intentRouter.route(IntentType.FLIGHT)).thenReturn(handler);
        when(handler.handle(any()))
                .thenReturn(OrchestrationResult.clarify("Nereye gitmek istersin?", "flight"));
        when(replyLocalizer.shouldLocalize("en")).thenReturn(true);
        when(replyLocalizer.localize("Nereye gitmek istersin?", "en"))
                .thenReturn("Where do you want to go?");

        OrchestrationResult result = service().handle("s1", "a flight", CALLER).result();

        assertThat(result.reply()).isEqualTo("Where do you want to go?");
        assertThat(result.pendingQuestion()).isEqualTo("Where do you want to go?");
        assertThat(result.domain()).isEqualTo("flight");
        // Reused, not re-translated: exactly one call for that string.
        verify(replyLocalizer, times(1)).localize("Nereye gitmek istersin?", "en");
    }

    /**
     * A disambiguation card must be fully localized: the question, and each option's button label AND
     * the value re-submitted on click — otherwise clicking a button would send Turkish back and flip
     * the conversation's language on the next turn.
     */
    @Test
    void choicesResult_localizesQuestionAndEachOptionLabelAndValue() {
        ChatSession session = new ChatSession("s1");
        when(sessionStore.getOrCreate(any(), any())).thenReturn(session);
        when(intentExtraction.extract(eq("Antalya"), anyList()))
                .thenReturn(new IntentExtractionResult(IntentType.AMBIGUOUS, null, "en",
                        com.paximum.paxassist.ai.LanguageConfidence.HIGH));
        when(intentRouter.route(IntentType.AMBIGUOUS)).thenReturn(handler);
        when(handler.handle(any())).thenReturn(OrchestrationResult.choices(
                "Otel araması mı yoksa uçuş araması mı yapmak istersin?",
                java.util.List.of(
                        new ChoiceOption("Otel ara", "Antalya için otel arıyorum"),
                        new ChoiceOption("Uçuş ara", "Antalya için uçuş arıyorum"))));
        when(replyLocalizer.shouldLocalize("en")).thenReturn(true);
        when(replyLocalizer.localize("Otel araması mı yoksa uçuş araması mı yapmak istersin?", "en"))
                .thenReturn("Do you want to search for a hotel or a flight?");
        when(replyLocalizer.localize("Otel ara", "en")).thenReturn("Search hotels");
        when(replyLocalizer.localize("Antalya için otel arıyorum", "en"))
                .thenReturn("I'm looking for a hotel in Antalya");
        when(replyLocalizer.localize("Uçuş ara", "en")).thenReturn("Search flights");
        when(replyLocalizer.localize("Antalya için uçuş arıyorum", "en"))
                .thenReturn("I'm looking for a flight to Antalya");

        OrchestrationResult result = service().handle("s1", "Antalya", CALLER).result();

        assertThat(result.reply()).isEqualTo("Do you want to search for a hotel or a flight?");
        assertThat(result.options()).extracting(ChoiceOption::label)
                .containsExactly("Search hotels", "Search flights");
        assertThat(result.options()).extracting(ChoiceOption::value)
                .containsExactly("I'm looking for a hotel in Antalya", "I'm looking for a flight to Antalya");
    }

    /**
     * Language is decided per message, not fixed for the session: Turkish → English → Turkish must
     * localize only the middle (English) turn. The final short "2" turn stands in for a low-confidence
     * message where the extractor has already carried the previous language forward (here English), so
     * the reply is localized to that carried-over language rather than flipping.
     */
    @Test
    void languageIsResolvedPerTurn_notStuckForTheSession() {
        ChatSession session = new ChatSession("s1");
        when(sessionStore.getOrCreate(any(), any())).thenReturn(session);
        when(intentRouter.route(IntentType.HOTEL)).thenReturn(handler);
        when(handler.handle(any()))
                .thenReturn(OrchestrationResult.message("Kaç gece konaklayacaksın?"));
        when(replyLocalizer.shouldLocalize("tr")).thenReturn(false);
        when(replyLocalizer.shouldLocalize("en")).thenReturn(true);
        when(replyLocalizer.localize("Kaç gece konaklayacaksın?", "en")).thenReturn("How many nights?");

        // Turn 1 — Turkish: no localization.
        when(intentExtraction.extract(eq("Antalya'da otel"), anyList()))
                .thenReturn(new IntentExtractionResult(IntentType.HOTEL, null, "tr",
                        com.paximum.paxassist.ai.LanguageConfidence.HIGH));
        assertThat(service().handle("s1", "Antalya'da otel", CALLER).result().reply())
                .isEqualTo("Kaç gece konaklayacaksın?");

        // Turn 2 — English: localized.
        when(intentExtraction.extract(eq("a hotel in Antalya"), anyList()))
                .thenReturn(new IntentExtractionResult(IntentType.HOTEL, null, "en",
                        com.paximum.paxassist.ai.LanguageConfidence.HIGH));
        assertThat(service().handle("s1", "a hotel in Antalya", CALLER).result().reply())
                .isEqualTo("How many nights?");

        // Turn 3 — back to Turkish: no localization again.
        when(intentExtraction.extract(eq("tekrar Türkçe"), anyList()))
                .thenReturn(new IntentExtractionResult(IntentType.HOTEL, null, "tr",
                        com.paximum.paxassist.ai.LanguageConfidence.HIGH));
        assertThat(service().handle("s1", "tekrar Türkçe", CALLER).result().reply())
                .isEqualTo("Kaç gece konaklayacaksın?");

        // Turn 4 — short "2", low confidence: extractor carried English forward → still English.
        when(intentExtraction.extract(eq("2"), anyList()))
                .thenReturn(new IntentExtractionResult(IntentType.HOTEL, null, "en",
                        com.paximum.paxassist.ai.LanguageConfidence.LOW));
        assertThat(service().handle("s1", "2", CALLER).result().reply())
                .isEqualTo("How many nights?");
    }

    /**
     * The OTHER path already answers in the user's language (FallbackHandler → ChatService), so the
     * orchestrator must NOT run a redundant localization pass over it.
     */
    @Test
    void otherIntent_isNotReLocalized_evenForNonTurkishLanguage() {
        ChatSession session = new ChatSession("s1");
        when(sessionStore.getOrCreate(any(), any())).thenReturn(session);
        when(intentExtraction.extract(eq("what's the weather"), anyList()))
                .thenReturn(new IntentExtractionResult(IntentType.OTHER, null, "en",
                        com.paximum.paxassist.ai.LanguageConfidence.HIGH));
        when(intentRouter.route(IntentType.OTHER)).thenReturn(handler);
        when(handler.handle(any()))
                .thenReturn(OrchestrationResult.message("I can only help with hotel or flight search."));

        OrchestrationOutcome outcome = service().handle("s1", "what's the weather", CALLER);

        assertThat(outcome.result().reply()).isEqualTo("I can only help with hotel or flight search.");
        verifyNoInteractions(replyLocalizer);
    }

    @Test
    void inScopeTurn_registersAsNonOutOfScope_resettingStreak() {
        ChatSession session = new ChatSession("s1");
        when(sessionStore.getOrCreate(any(), any())).thenReturn(session);
        when(intentExtraction.extract(eq("Antalya'da otel"), anyList()))
                .thenReturn(new IntentExtractionResult(IntentType.HOTEL, null));
        when(intentRouter.route(IntentType.HOTEL)).thenReturn(handler);
        when(handler.handle(any())).thenReturn(OrchestrationResult.message("8 otel buldum"));

        service().handle("s1", "Antalya'da otel", CALLER);

        verify(guard).registerOutOfScope(7L, false);
        verify(sessionStore).save(session);
    }
}
