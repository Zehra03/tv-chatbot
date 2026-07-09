package com.paximum.paxassist.orchestrator.intent;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
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
import com.paximum.paxassist.flight.domain.FlightProduct;
import com.paximum.paxassist.flight.service.FlightSearchOutcome;
import com.paximum.paxassist.flight.service.FlightSearchService;
import com.paximum.paxassist.orchestrator.OrchestrationContext;
import com.paximum.paxassist.orchestrator.OrchestrationResult;
import com.paximum.paxassist.orchestrator.clarify.ClarificationCatalog;
import com.paximum.paxassist.orchestrator.date.TravelDateGuard;
import com.paximum.paxassist.orchestrator.mapper.FlightCriteriaMapper;
import com.paximum.paxassist.orchestrator.slot.SlotFillingService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FlightSearchHandlerTest {

    @Mock
    private SlotFillingService slotFilling;
    @Mock
    private FlightSearchService flightSearchService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private final TravelDateGuard dateGuard =
            new TravelDateGuard(Clock.fixed(Instant.parse("2026-07-08T00:00:00Z"), ZoneOffset.UTC));

    private FlightSearchHandler handler() {
        return new FlightSearchHandler(
                slotFilling, new FlightCriteriaMapper(), flightSearchService, new ClarificationCatalog(), dateGuard);
    }

    private OrchestrationContext contextWith(SlotCriteria merged) {
        when(slotFilling.accumulate(any(), any())).thenReturn(merged);
        return new OrchestrationContext(new ChatSession("s1"), "uçuş", IntentType.FLIGHT, merged);
    }

    private SlotCriteria slots(Map<String, Object> fields) {
        return objectMapper.convertValue(fields, SlotCriteria.class);
    }

    private FlightProduct flight(String id, String price) {
        return FlightProduct.builder()
                .id(id).airline("TK").flightNumber("TK1").origin("IST").destination("AYT")
                .stops(0).durationMinutes(70).price(new BigDecimal(price)).currency("TRY").build();
    }

    @Test
    void incompleteSearchBecomesClarifyingQuestion() {
        OrchestrationContext context = contextWith(slots(Map.of("origin", "İstanbul")));
        when(flightSearchService.search(any()))
                .thenReturn(FlightSearchOutcome.incomplete(List.of("destination")));

        OrchestrationResult result = handler().handle(context);

        assertThat(result.cards()).isEmpty();
        assertThat(result.reply()).contains("Nereye");
    }

    @Test
    void successReturnsCards() {
        SlotCriteria merged = slots(Map.of(
                "origin", "İstanbul", "destination", "Antalya", "departureDate", "2026-08-01",
                "adults", 1, "currency", "TRY"));
        OrchestrationContext context = contextWith(merged);
        FlightProduct f1 = flight("F1", "1450");
        when(flightSearchService.search(any())).thenReturn(FlightSearchOutcome.complete(List.of(f1)));

        OrchestrationResult result = handler().handle(context);

        assertThat(result.cards()).containsExactly(f1);
        assertThat(context.session().getActiveDomain()).isEqualTo("FLIGHT");
    }

    @Test
    void pastDepartureBecomesFriendlyClarifyBeforeSearch() {
        OrchestrationContext context = contextWith(slots(Map.of(
                "origin", "İstanbul", "destination", "Antalya", "departureDate", "2026-06-25", "adults", 1)));

        OrchestrationResult result = handler().handle(context);

        assertThat(result.cards()).isEmpty();
        assertThat(result.reply()).contains("geçmiş");
        verifyNoInteractions(flightSearchService);
    }

    @Test
    void maxPriceFiltersOutTooExpensiveFlights() {
        SlotCriteria merged = slots(Map.of(
                "origin", "İstanbul", "destination", "Antalya", "departureDate", "2026-08-01",
                "adults", 1, "currency", "TRY", "maxPrice", 1500));
        OrchestrationContext context = contextWith(merged);
        FlightProduct cheap = flight("F1", "1450");
        FlightProduct pricey = flight("F2", "2500");
        when(flightSearchService.search(any()))
                .thenReturn(FlightSearchOutcome.complete(List.of(cheap, pricey)));

        OrchestrationResult result = handler().handle(context);

        assertThat(result.cards()).containsExactly(cheap);
    }
}
