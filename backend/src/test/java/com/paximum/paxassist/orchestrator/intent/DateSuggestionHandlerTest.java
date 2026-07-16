package com.paximum.paxassist.orchestrator.intent;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.fasterxml.jackson.databind.ObjectMapper;

import com.paximum.paxassist.ai.IntentType;
import com.paximum.paxassist.ai.SlotCriteria;
import com.paximum.paxassist.chat.domain.ChatSession;
import com.paximum.paxassist.hotel.HotelSearchService;
import com.paximum.paxassist.hotel.dto.HotelSearchRequest;
import com.paximum.paxassist.orchestrator.OrchestrationContext;
import com.paximum.paxassist.orchestrator.OrchestrationResult;
import com.paximum.paxassist.orchestrator.mapper.GeoCountryResolver;
import com.paximum.paxassist.orchestrator.mapper.HotelCriteriaMapper;
import com.paximum.paxassist.orchestrator.slot.SlotFillingService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DateSuggestionHandlerTest {

    @Mock
    private SlotFillingService slotFilling;
    @Mock
    private HotelSearchService hotelSearchService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private DateSuggestionHandler handler() {
        return new DateSuggestionHandler(
                slotFilling, new HotelCriteriaMapper(new GeoCountryResolver()), hotelSearchService, 2, 14, 3);
    }

    private OrchestrationContext contextWith(SlotCriteria merged) {
        when(slotFilling.accumulate(any(), any())).thenReturn(merged);
        return new OrchestrationContext(
                new ChatSession("s1"), "başka hangi tarihte var", IntentType.DATE_ALTERNATIVES, merged);
    }

    private SlotCriteria slots(Map<String, Object> fields) {
        return objectMapper.convertValue(fields, SlotCriteria.class);
    }

    @Test
    void supportsOnlyDateAlternatives() {
        DateSuggestionHandler handler = handler();
        assertThat(handler.supports(IntentType.DATE_ALTERNATIVES)).isTrue();
        assertThat(handler.supports(IntentType.HOTEL)).isFalse();
        assertThat(handler.supports(IntentType.OTHER)).isFalse();
    }

    @Test
    void offersAvailableDatesWhenProbeFindsThem() {
        SlotCriteria merged = slots(Map.of(
                "location", "Urla", "checkIn", "2026-08-04", "nights", 6, "adults", 1));
        OrchestrationContext context = contextWith(merged);
        when(hotelSearchService.suggestAvailableCheckInDates(any(HotelSearchRequest.class), anyInt(), anyInt(), anyInt()))
                .thenReturn(List.of("2026-08-06", "2026-08-11"));

        OrchestrationResult result = handler().handle(context);

        assertThat(result.reply()).contains("2026-08-06").contains("2026-08-11");
        assertThat(result.cards()).isEmpty();
        assertThat(context.session().getActiveDomain()).isEqualTo("HOTEL");
    }

    @Test
    void honestMessageWhenNoAlternativeDatesFound() {
        SlotCriteria merged = slots(Map.of(
                "location", "Urla", "checkIn", "2026-08-04", "nights", 6, "adults", 1));
        OrchestrationContext context = contextWith(merged);
        when(hotelSearchService.suggestAvailableCheckInDates(any(HotelSearchRequest.class), anyInt(), anyInt(), anyInt()))
                .thenReturn(List.of());

        OrchestrationResult result = handler().handle(context);

        assertThat(result.reply()).contains("Urla").contains("bulamadım");
        assertThat(result.cards()).isEmpty();
    }

    @Test
    void nudgesWhenNoDestinationAccumulatedAndDoesNotProbe() {
        OrchestrationContext context = contextWith(slots(Map.of()));

        OrchestrationResult result = handler().handle(context);

        assertThat(result.reply()).contains("Hangi şehir");
        verifyNoInteractions(hotelSearchService);
    }
}
