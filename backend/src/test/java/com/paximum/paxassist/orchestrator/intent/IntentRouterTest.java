package com.paximum.paxassist.orchestrator.intent;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.paximum.paxassist.ai.IntentType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class IntentRouterTest {

    @Mock
    private IntentHandler hotelHandler;
    @Mock
    private FallbackHandler fallbackHandler;

    private IntentRouter router() {
        lenient().when(hotelHandler.supports(IntentType.HOTEL)).thenReturn(true);
        return new IntentRouter(List.of(hotelHandler, fallbackHandler), fallbackHandler);
    }

    @Test
    void routesToMatchingHandler() {
        assertThat(router().route(IntentType.HOTEL)).isSameAs(hotelHandler);
    }

    @Test
    void fallsBackWhenNoHandlerMatches() {
        assertThat(router().route(IntentType.FLIGHT)).isSameAs(fallbackHandler);
    }

    @Test
    void fallsBackWhenIntentIsNull() {
        assertThat(router().route(null)).isSameAs(fallbackHandler);
    }
}
