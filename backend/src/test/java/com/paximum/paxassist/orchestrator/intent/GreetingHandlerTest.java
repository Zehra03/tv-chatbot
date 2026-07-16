package com.paximum.paxassist.orchestrator.intent;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.paximum.paxassist.ai.IntentType;
import com.paximum.paxassist.auth.service.GreetingNameService;
import com.paximum.paxassist.chat.domain.ChatSession;
import com.paximum.paxassist.orchestrator.OrchestrationContext;
import com.paximum.paxassist.orchestrator.OrchestrationResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GreetingHandlerTest {

    @Mock
    private GreetingNameService greetingNameService;

    private OrchestrationContext greeting(Long userId) {
        ChatSession session = new ChatSession("s1");
        session.setUserId(userId);
        return new OrchestrationContext(session, "merhaba", IntentType.GREETING, null);
    }

    @Test
    void supportsOnlyGreetingIntent() {
        GreetingHandler handler = new GreetingHandler(greetingNameService);
        assertThat(handler.supports(IntentType.GREETING)).isTrue();
        assertThat(handler.supports(IntentType.OTHER)).isFalse();
        assertThat(handler.supports(IntentType.HOTEL)).isFalse();
    }

    @Test
    void greetsAGuestWithoutAName() {
        GreetingHandler handler = new GreetingHandler(greetingNameService);
        when(greetingNameService.firstNameOf(null)).thenReturn(Optional.empty());

        OrchestrationResult result = handler.handle(greeting(null));

        assertThat(result.reply()).isEqualTo(
                "Merhaba! Ben seyahat asistanın Paxi. Sana nasıl yardımcı olabilirim? "
                        + "Otel mi yoksa uçuş mu arıyorsun?");
    }

    @Test
    void greetsALoggedInUserByName() {
        GreetingHandler handler = new GreetingHandler(greetingNameService);
        when(greetingNameService.firstNameOf(7L)).thenReturn(Optional.of("Deniz"));

        OrchestrationResult result = handler.handle(greeting(7L));

        assertThat(result.reply()).isEqualTo(
                "Merhaba Deniz! Ben seyahat asistanın Paxi. Sana nasıl yardımcı olabilirim? "
                        + "Otel mi yoksa uçuş mu arıyorsun?");
    }

    @Test
    void greetingIsAPlainMessageWithNoCardsOrRedirect() {
        GreetingHandler handler = new GreetingHandler(greetingNameService);
        when(greetingNameService.firstNameOf(null)).thenReturn(Optional.empty());

        OrchestrationResult result = handler.handle(greeting(null));

        assertThat(result.cards()).isEmpty();
        assertThat(result.redirectToReservation()).isFalse();
    }
}
