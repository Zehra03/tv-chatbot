package com.paximum.paxassist.orchestrator.intent;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.fasterxml.jackson.databind.ObjectMapper;

import com.paximum.paxassist.ai.IntentType;
import com.paximum.paxassist.ai.SlotCriteria;
import com.paximum.paxassist.chat.domain.ChatSession;
import com.paximum.paxassist.flight.domain.FlightProduct;
import com.paximum.paxassist.flight.domain.FlightSearchCriteria;
import com.paximum.paxassist.flight.domain.TripType;
import com.paximum.paxassist.flight.service.FlightSearchOutcome;
import com.paximum.paxassist.flight.service.FlightSearchService;
import com.paximum.paxassist.orchestrator.OrchestrationContext;
import com.paximum.paxassist.orchestrator.OrchestrationResult;
import com.paximum.paxassist.orchestrator.clarify.ClarificationCatalog;
import com.paximum.paxassist.orchestrator.slot.LocationGuard;
import com.paximum.paxassist.orchestrator.clarify.ClarificationComposer;
import com.paximum.paxassist.orchestrator.clarify.NoPreferenceDetector;
import com.paximum.paxassist.orchestrator.slot.SlotGuard;
import com.paximum.paxassist.orchestrator.mapper.FlightCriteriaMapper;
import com.paximum.paxassist.orchestrator.mapper.GeoCountryResolver;
import com.paximum.paxassist.orchestrator.slot.SlotFillingService;
import com.paximum.paxassist.orchestrator.slot.TripTypeDetector;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FlightSearchHandlerTest {

    @Mock
    private SlotFillingService slotFilling;
    @Mock
    private FlightSearchService flightSearchService;

    private SlotGuard slotGuard;
    private LocationGuard locationGuard;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private FlightSearchHandler handler() {
        slotGuard = mock(SlotGuard.class);
        locationGuard = mock(LocationGuard.class);
        org.mockito.Mockito.lenient().when(slotGuard.checkInvalidSlots(any())).thenReturn(Optional.empty());
        org.mockito.Mockito.lenient().when(locationGuard.checkInvalidLocation(any(), any())).thenReturn(Optional.empty());
        return new FlightSearchHandler(
                slotFilling, new FlightCriteriaMapper(new GeoCountryResolver()), flightSearchService,
                new ClarificationComposer(new ClarificationCatalog(), new NoPreferenceDetector()), slotGuard, locationGuard,
                new TripTypeDetector());
    }

    private OrchestrationContext contextWith(SlotCriteria merged) {
        org.mockito.Mockito.lenient().when(slotFilling.peekMerge(any(), any())).thenReturn(merged);
        org.mockito.Mockito.lenient().when(slotFilling.accumulate(any(), any())).thenReturn(merged);
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
    void childWithoutAges_asksForThemInsteadOfMispricingTheFare() {
        // Without the age we cannot tell an infant from a child, and the two are priced differently.
        OrchestrationContext context = contextWith(slots(Map.of(
                "origin", "İstanbul", "destination", "Antalya", "departureDate", "2026-08-01",
                "adults", 2, "children", 1, "currency", "TRY")));

        OrchestrationResult result = handler().handle(context);

        assertThat(result.cards()).isEmpty();
        assertThat(result.reply()).contains("yaş");
        org.mockito.Mockito.verifyNoInteractions(flightSearchService);
    }

    @Test
    void childAgesPresent_searchRunsWithTypedPassengers() {
        SlotCriteria merged = slots(Map.of(
                "origin", "İstanbul", "destination", "Antalya", "departureDate", "2026-08-01",
                "adults", 2, "children", 2, "childAges", List.of(1, 8), "currency", "TRY"));
        OrchestrationContext context = contextWith(merged);
        FlightProduct f1 = flight("F1", "1450");
        when(flightSearchService.search(any())).thenReturn(FlightSearchOutcome.complete(List.of(f1)));

        OrchestrationResult result = handler().handle(context);

        assertThat(result.cards()).containsExactly(f1);

        ArgumentCaptor<FlightSearchCriteria> captor = ArgumentCaptor.forClass(FlightSearchCriteria.class);
        verify(flightSearchService).search(captor.capture());
        assertThat(captor.getValue().getPassengers().getInfants()).isEqualTo(1);
        assertThat(captor.getValue().getPassengers().getChildren()).isEqualTo(1);
        assertThat(captor.getValue().getPassengers().getAdults()).isEqualTo(2);
    }

    @Test
    void partyOverNineSeats_isRefusedBeforeAnySearch() {
        OrchestrationContext context = contextWith(slots(Map.of(
                "origin", "İstanbul", "destination", "Antalya", "departureDate", "2026-08-01",
                "adults", 10, "currency", "TRY")));

        OrchestrationResult result = handler().handle(context);

        assertThat(result.cards()).isEmpty();
        assertThat(result.reply()).contains("9");
        org.mockito.Mockito.verifyNoInteractions(flightSearchService);
    }

    @Test
    void nineSeatsPlusLapInfants_stillSearches() {
        // Infants take no seat, so 8 adults + a child + 2 infants is 9 seats — within the limit.
        SlotCriteria merged = slots(Map.of(
                "origin", "İstanbul", "destination", "Antalya", "departureDate", "2026-08-01",
                "adults", 8, "children", 3, "childAges", List.of(5, 1, 0), "currency", "TRY"));
        when(flightSearchService.search(any())).thenReturn(FlightSearchOutcome.complete(List.of(flight("F1", "1450"))));

        OrchestrationResult result = handler().handle(contextWith(merged));

        assertThat(result.cards()).hasSize(1);
    }

    @Test
    void moreInfantsThanAdults_isRefusedWithTheReason() {
        OrchestrationContext context = contextWith(slots(Map.of(
                "origin", "İstanbul", "destination", "Antalya", "departureDate", "2026-08-01",
                "adults", 1, "children", 2, "childAges", List.of(0, 1), "currency", "TRY")));

        OrchestrationResult result = handler().handle(context);

        assertThat(result.cards()).isEmpty();
        assertThat(result.reply()).contains("kucağında");
        org.mockito.Mockito.verifyNoInteractions(flightSearchService);
    }

    @Test
    void baggageRequestReachesTheSearchCriteria_notAPostFilter() {
        SlotCriteria merged = slots(Map.of(
                "origin", "İstanbul", "destination", "Antalya", "departureDate", "2026-08-01",
                "adults", 1, "minCheckedBaggageKg", 20, "currency", "TRY"));
        when(flightSearchService.search(any())).thenReturn(FlightSearchOutcome.complete(List.of(flight("F1", "1450"))));

        handler().handle(contextWith(merged));

        ArgumentCaptor<FlightSearchCriteria> captor = ArgumentCaptor.forClass(FlightSearchCriteria.class);
        verify(flightSearchService).search(captor.capture());
        assertThat(captor.getValue().getMinCheckedBaggageKg()).isEqualTo(20);
    }

    @Test
    void noFareMeetsTheBaggageRequest_saysSoRatherThanBlamingTheRoute() {
        SlotCriteria merged = slots(Map.of(
                "origin", "İstanbul", "destination", "Antalya", "departureDate", "2026-08-01",
                "adults", 1, "minCheckedBaggageKg", 30, "currency", "TRY"));
        when(flightSearchService.search(any())).thenReturn(FlightSearchOutcome.complete(List.of()));

        OrchestrationResult result = handler().handle(contextWith(merged));

        assertThat(result.cards()).isEmpty();
        assertThat(result.reply()).contains("30 kg").contains("bagaj");
    }

    @Test
    void pastDateGuard_shortCircuitsBeforeSearch() {
        FlightSearchHandler handler = handler();
        when(slotGuard.checkInvalidSlots(any()))
                .thenReturn(Optional.of("Girdiğiniz tarih geçmişte kalıyor"));

        OrchestrationContext context = contextWith(slots(Map.of("departureDate", "2026-06-01")));

        OrchestrationResult result = handler.handle(context);

        assertThat(result.cards()).isEmpty();
        assertThat(result.reply()).contains("geçmiş");
        org.mockito.Mockito.verifyNoInteractions(flightSearchService);
    }

    @Test
    void invalidLocationGuard_shortCircuitsBeforeSearch() {
        FlightSearchHandler handler = handler();
        when(locationGuard.checkInvalidLocation(any(), any()))
                .thenReturn(Optional.of("Girdiğin kalkış noktası (burdan) sistemimizde bulunamadı."));

        OrchestrationContext context = contextWith(slots(Map.of("origin", "burdan")));
        context.session().getAccumulatedCriteria().put("origin", "burdan");

        OrchestrationResult result = handler.handle(context);

        assertThat(result.cards()).isEmpty();
        assertThat(result.reply()).contains("sistemimizde bulunamadı");
        assertThat(context.session().getAccumulatedCriteria()).doesNotContainKey("origin");
        assertThat(context.session().getAccumulatedCriteria()).doesNotContainKey("destination");
        org.mockito.Mockito.verifyNoInteractions(flightSearchService);
    }

    @Test
    void maxPriceFiltersOutTooExpensiveFlights() {
        SlotCriteria merged = slots(Map.of(
                "origin", "İstanbul", "destination", "Antalya", "departureDate", "2026-08-01",
                "adults", 1, "currency", "TRY", "flightMaxPrice", 1500));
        OrchestrationContext context = contextWith(merged);
        FlightProduct cheap = flight("F1", "1450");
        FlightProduct pricey = flight("F2", "2500");
        when(flightSearchService.search(any()))
                .thenReturn(FlightSearchOutcome.complete(List.of(cheap, pricey)));

        OrchestrationResult result = handler().handle(context);

        assertThat(result.cards()).containsExactly(cheap);
    }

    /**
     * The chat's round-trip gap: the user says "gidiş-dönüş" but names no return date yet. The trip
     * type used to be inferred from the (absent) date alone, so the search ran ONE_WAY and the user
     * was handed outbound-only results for a round-trip request, with nothing asked in between.
     */
    @Test
    void roundTripRequestWithoutAReturnDate_asksForTheDateInsteadOfSearchingOneWay() {
        SlotCriteria stated = slots(Map.of(
                "origin", "İstanbul", "destination", "Antalya", "departureDate", "2026-08-01",
                "adults", 1, "currency", "TRY"));
        OrchestrationContext context = contextPassingSlotsThrough(
                "Gidiş-dönüş uçuş arıyorum", stated);
        answerSearchFromTheCriteriaItself();

        OrchestrationResult result = handler().handle(context);

        assertThat(result.cards()).isEmpty();
        assertThat(result.reply()).contains("Dönüş tarihin");

        ArgumentCaptor<FlightSearchCriteria> captor = ArgumentCaptor.forClass(FlightSearchCriteria.class);
        verify(flightSearchService).search(captor.capture());
        assertThat(captor.getValue().getTripType()).isEqualTo(TripType.ROUND_TRIP);
    }

    @Test
    void onceTheReturnDateArrives_theRoundTripSearchRuns() {
        SlotCriteria answered = slots(Map.of(
                "origin", "İstanbul", "destination", "Antalya", "departureDate", "2026-08-01",
                "returnDate", "2026-08-08", "adults", 1, "currency", "TRY", "tripType", "round_trip"));
        OrchestrationContext context = contextPassingSlotsThrough("8 Ağustos'ta dönüyorum", answered);
        FlightProduct f1 = flight("F1", "1450");
        when(flightSearchService.search(any())).thenReturn(FlightSearchOutcome.complete(List.of(f1)));

        OrchestrationResult result = handler().handle(context);

        assertThat(result.cards()).containsExactly(f1);

        ArgumentCaptor<FlightSearchCriteria> captor = ArgumentCaptor.forClass(FlightSearchCriteria.class);
        verify(flightSearchService).search(captor.capture());
        assertThat(captor.getValue().getTripType()).isEqualTo(TripType.ROUND_TRIP);
        assertThat(captor.getValue().getReturnDate()).isEqualTo(java.time.LocalDate.of(2026, 8, 8));
    }

    @Test
    void sayingTekYonDropsTheReturnDateTheUserNoLongerWants() {
        SlotCriteria stated = slots(Map.of("origin", "İstanbul", "destination", "Antalya",
                "departureDate", "2026-08-01", "adults", 1, "currency", "TRY"));
        OrchestrationContext context = contextPassingSlotsThrough("tek yön olsun", stated);
        context.session().getAccumulatedCriteria().put("returnDate", "2026-08-08");
        when(flightSearchService.search(any())).thenReturn(FlightSearchOutcome.complete(List.of()));

        handler().handle(context);

        assertThat(context.session().getAccumulatedCriteria()).doesNotContainKey("returnDate");
        ArgumentCaptor<FlightSearchCriteria> captor = ArgumentCaptor.forClass(FlightSearchCriteria.class);
        verify(flightSearchService).search(captor.capture());
        assertThat(captor.getValue().getTripType()).isEqualTo(TripType.ONE_WAY);
    }

    /**
     * Slot filling is mocked to hand back exactly what the handler passes in, so what the handler
     * itself adds to the turn's criteria (the trip type it read from the message) is visible.
     */
    private OrchestrationContext contextPassingSlotsThrough(String userMessage, SlotCriteria criteria) {
        org.mockito.Mockito.lenient().when(slotFilling.peekMerge(any(), any()))
                .thenAnswer(invocation -> invocation.getArgument(1));
        org.mockito.Mockito.lenient().when(slotFilling.accumulate(any(), any()))
                .thenAnswer(invocation -> invocation.getArgument(1));
        return new OrchestrationContext(new ChatSession("s1"), userMessage, IntentType.FLIGHT, criteria);
    }

    /** Mirrors the real service: a criteria that is still missing a required field is incomplete. */
    private void answerSearchFromTheCriteriaItself() {
        when(flightSearchService.search(any())).thenAnswer(invocation -> {
            FlightSearchCriteria criteria = invocation.getArgument(0);
            List<String> missing = criteria.missingRequiredFields();
            return missing.isEmpty()
                    ? FlightSearchOutcome.complete(List.of())
                    : FlightSearchOutcome.incomplete(missing);
        });
    }
}
