package com.paximum.paxassist.orchestrator.intent;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import com.paximum.paxassist.ai.IntentType;
import com.paximum.paxassist.auth.service.GreetingNameService;
import com.paximum.paxassist.chat.domain.ChatSession;
import com.paximum.paxassist.chat.dto.AiReply;
import com.paximum.paxassist.chat.service.ChatService;
import com.paximum.paxassist.orchestrator.OrchestrationContext;
import com.paximum.paxassist.orchestrator.OrchestrationResult;
import com.paximum.paxassist.validator.Cohort;
import com.paximum.paxassist.validator.ValidationOrchestrator;
import com.paximum.paxassist.validator.ValidationOutcome;
import com.paximum.paxassist.validator.ValidationResult;
import com.paximum.paxassist.validator.ValidationResult.Verdict;
import com.paximum.paxassist.validator.ValidatorMetrics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FallbackHandlerTest {

    private static final String SAFE_FALLBACK =
            "Bu konuda yardımcı olamıyorum. Otel veya uçuş araması için buradayım.";

    @Mock
    private ChatService chatService;
    @Mock
    private ValidationOrchestrator validationOrchestrator;
    @Mock
    private GreetingNameService greetingNameService;

    /** A guest turn: the session has no owner, so there is no name to greet by. */
    private OrchestrationContext otherContext(String message) {
        return new OrchestrationContext(new ChatSession("s1"), message, IntentType.OTHER, null);
    }

    private OrchestrationContext otherContextOwnedBy(String message, Long userId) {
        ChatSession session = new ChatSession("s1");
        session.setUserId(userId);
        return new OrchestrationContext(session, message, IntentType.OTHER, null);
    }

    private static ValidationOutcome approved() {
        return new ValidationOutcome(new ValidationResult(Verdict.APPROVED, "ok"),
                false, Cohort.B_TREATMENT, ValidatorMetrics.none());
    }

    private static ValidationOutcome rejected(boolean retryAllowed, String feedback) {
        return new ValidationOutcome(new ValidationResult(Verdict.REJECTED, feedback),
                retryAllowed, Cohort.B_TREATMENT, ValidatorMetrics.none());
    }

    @Test
    void supportsOnlyOtherIntent() {
        FallbackHandler handler = new FallbackHandler(chatService, validationOrchestrator, greetingNameService);
        assertThat(handler.supports(IntentType.OTHER)).isTrue();
        assertThat(handler.supports(IntentType.HOTEL)).isFalse();
    }

    @Test
    void returnsCandidateWhenValidatorApprovesOnFirstPass() {
        FallbackHandler handler = new FallbackHandler(chatService, validationOrchestrator, greetingNameService);
        when(chatService.chat(anyString(), any()))
                .thenReturn(new AiReply("Size otel veya uçuş aramasında yardımcı olabilirim."));
        when(validationOrchestrator.validate(eq("s1"), eq("merhaba"), anyString(), any(), anyInt()))
                .thenReturn(approved());

        OrchestrationResult result = handler.handle(otherContext("merhaba"));

        assertThat(result.reply()).isEqualTo("Size otel veya uçuş aramasında yardımcı olabilirim.");
        assertThat(result.redirectToReservation()).isFalse();
        assertThat(result.cards()).isEmpty();
        verify(chatService, times(1)).chat(anyString(), any());
    }

    @Test
    void retriesWithFeedbackThenReturnsApprovedRetry() {
        FallbackHandler handler = new FallbackHandler(chatService, validationOrchestrator, greetingNameService);
        when(chatService.chat(anyString(), any()))
                .thenReturn(new AiReply("ilk taslak"))
                .thenReturn(new AiReply("düzeltilmiş yanıt"));
        when(validationOrchestrator.validate(eq("s1"), eq("merhaba"), anyString(), any(), anyInt()))
                .thenReturn(rejected(true, "Kapsam dışına çıkma."))
                .thenReturn(approved());

        OrchestrationResult result = handler.handle(otherContext("merhaba"));

        assertThat(result.reply()).isEqualTo("düzeltilmiş yanıt");

        // The second generate call must carry the validator's feedback so the model can correct itself.
        ArgumentCaptor<String> prompts = ArgumentCaptor.forClass(String.class);
        verify(chatService, times(2)).chat(prompts.capture(), any());
        assertThat(prompts.getAllValues().get(0)).isEqualTo("merhaba");
        assertThat(prompts.getAllValues().get(1)).contains("Kapsam dışına çıkma.");
    }

    @Test
    void returnsSafeFallbackWhenRejectedAndNoRetryRemains() {
        FallbackHandler handler = new FallbackHandler(chatService, validationOrchestrator, greetingNameService);
        when(chatService.chat(anyString(), any())).thenReturn(new AiReply("uydurma fiyatlı yanıt"));
        when(validationOrchestrator.validate(eq("s1"), eq("merhaba"), anyString(), any(), anyInt()))
                .thenReturn(rejected(false, "Uydurma fiyat içeriyor."));

        OrchestrationResult result = handler.handle(otherContext("merhaba"));

        assertThat(result.reply()).isEqualTo(SAFE_FALLBACK);
    }

    @Test
    void passesTheSessionIdAsTraceIdSoVerdictsAreTraceableToTheConversation() {
        // Every validator.feedback log line must carry this turn's session id, otherwise a rejection
        // cannot be tied back to the scenario run that produced it (test-catalogue fail records).
        FallbackHandler handler = new FallbackHandler(chatService, validationOrchestrator, greetingNameService);
        when(chatService.chat(anyString(), any())).thenReturn(new AiReply("yanıt"));
        when(validationOrchestrator.validate(anyString(), anyString(), anyString(), any(), anyInt()))
                .thenReturn(approved());

        handler.handle(otherContext("merhaba"));

        verify(validationOrchestrator).validate(eq("s1"), eq("merhaba"), eq("yanıt"), any(), eq(1));
    }

    @Test
    void passesTheOwnersFirstNameSoPaxiCanGreetThemByName() {
        FallbackHandler handler = new FallbackHandler(chatService, validationOrchestrator, greetingNameService);
        when(greetingNameService.firstNameOf(7L)).thenReturn(Optional.of("Deniz"));
        when(chatService.chat(anyString(), any())).thenReturn(new AiReply("Merhaba Deniz! Ben Paxi."));
        when(validationOrchestrator.validate(anyString(), anyString(), anyString(), any(), anyInt()))
                .thenReturn(approved());

        handler.handle(otherContextOwnedBy("selam", 7L));

        verify(chatService).chat("selam", "Deniz");
    }

    @Test
    void passesNoNameForAGuestSession() {
        FallbackHandler handler = new FallbackHandler(chatService, validationOrchestrator, greetingNameService);
        when(greetingNameService.firstNameOf(null)).thenReturn(Optional.empty());
        when(chatService.chat(anyString(), any())).thenReturn(new AiReply("Merhaba! Ben Paxi."));
        when(validationOrchestrator.validate(anyString(), anyString(), anyString(), any(), anyInt()))
                .thenReturn(approved());

        handler.handle(otherContext("selam"));

        verify(chatService).chat("selam", null);
    }

    @Test
    void failsOpenAndReturnsCandidateWhenValidatorThrows() {
        FallbackHandler handler = new FallbackHandler(chatService, validationOrchestrator, greetingNameService);
        when(chatService.chat(anyString(), any())).thenReturn(new AiReply("doğrulanamayan yanıt"));
        when(validationOrchestrator.validate(eq("s1"), eq("merhaba"), anyString(), any(), anyInt()))
                .thenThrow(new RuntimeException("validator down"));

        OrchestrationResult result = handler.handle(otherContext("merhaba"));

        assertThat(result.reply()).isEqualTo("doğrulanamayan yanıt");
    }
}
