package com.paximum.paxassist.orchestrator.intent;

import java.math.BigDecimal;
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
import com.paximum.paxassist.hotel.HotelProduct;
import com.paximum.paxassist.hotel.HotelSearchService;
import com.paximum.paxassist.hotel.dto.HotelSearchResponse;
import com.paximum.paxassist.orchestrator.OrchestrationContext;
import com.paximum.paxassist.orchestrator.OrchestrationResult;
import com.paximum.paxassist.orchestrator.clarify.ClarificationCatalog;
import com.paximum.paxassist.orchestrator.mapper.HotelCriteriaMapper;
import com.paximum.paxassist.orchestrator.slot.SlotFillingService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HotelSearchHandlerTest {

    @Mock
    private SlotFillingService slotFilling;
    @Mock
    private HotelSearchService hotelSearchService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private HotelSearchHandler handler() {
        return new HotelSearchHandler(
                slotFilling, new HotelCriteriaMapper(), hotelSearchService, new ClarificationCatalog());
    }

    private OrchestrationContext contextWith(SlotCriteria merged) {
        when(slotFilling.accumulate(any(), any())).thenReturn(merged);
        return new OrchestrationContext(new ChatSession("s1"), "otel", IntentType.HOTEL, merged);
    }

    @Test
    void incompleteSearchBecomesClarifyingQuestion() {
        OrchestrationContext context = contextWith(objectMapper.convertValue(Map.of(), SlotCriteria.class));
        when(hotelSearchService.searchHotels(any()))
                .thenReturn(HotelSearchResponse.incomplete(List.of("destination")));

        OrchestrationResult result = handler().handle(context);

        assertThat(result.cards()).isEmpty();
        assertThat(result.redirectToReservation()).isFalse();
        assertThat(result.reply()).contains("şehir");
    }

    @Test
    void successReturnsCardsAndUpdatesSession() {
        SlotCriteria merged = objectMapper.convertValue(
                Map.of("location", "Antalya", "checkIn", "2026-08-01", "checkOut", "2026-08-05", "adults", 2),
                SlotCriteria.class);
        OrchestrationContext context = contextWith(merged);
        HotelProduct h1 = new HotelProduct("H1", "Rixos", "Antalya", 5, new BigDecimal("1500"), "EUR", "AI", true);
        HotelProduct h2 = new HotelProduct("H2", "Titanic", "Antalya", 5, new BigDecimal("2000"), "EUR", "AI", true);
        when(hotelSearchService.searchHotels(any()))
                .thenReturn(HotelSearchResponse.success(List.of(h1, h2)));

        OrchestrationResult result = handler().handle(context);

        assertThat(result.cards()).containsExactly(h1, h2);
        assertThat(context.session().getActiveDomain()).isEqualTo("HOTEL");
        assertThat(context.session().getLastResultCards()).containsExactly(h1, h2);
    }
}
