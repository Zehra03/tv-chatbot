package com.paximum.paxassist.ai;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * A bare greeting is classified without the model: that is what makes the greeting reply
 * verbatim-guaranteed (and free of a round-trip).
 */
@ExtendWith(MockitoExtension.class)
class IntentExtractionGreetingTest {

    @Mock
    private ChatClient chatClient;

    private final GreetingDetector greetingDetector = new GreetingDetector();

    @Test
    void classifiesABareGreetingWithoutCallingTheModel() {
        IntentExtractionService service = new IntentExtractionService(chatClient, greetingDetector);

        IntentExtractionResult result = service.extract("merhaba", List.of());

        assertThat(result.intent()).isEqualTo(IntentType.GREETING);
        assertThat(result.criteria()).isNull();
        verifyNoInteractions(chatClient);
    }
}
