package com.paximum.paxassist.orchestrator.intent;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Component;

import com.paximum.paxassist.ai.IntentType;
import com.paximum.paxassist.ai.SlotCriteria;
import com.paximum.paxassist.flight.domain.FlightSearchCriteria;
import com.paximum.paxassist.flight.service.FlightSearchOutcome;
import com.paximum.paxassist.flight.service.FlightSearchService;
import com.paximum.paxassist.orchestrator.OrchestrationContext;
import com.paximum.paxassist.orchestrator.OrchestrationResult;
import com.paximum.paxassist.orchestrator.clarify.ClarificationCatalog;
import com.paximum.paxassist.orchestrator.slot.SlotGuard;
import com.paximum.paxassist.orchestrator.mapper.FlightCriteriaMapper;
import com.paximum.paxassist.orchestrator.slot.SlotFillingService;

/**
 * Handles FLIGHT intent — the mirror of {@link HotelSearchHandler} for the
 * flight module.
 * Completeness is decided by {@link FlightSearchService}
 * ({@code FlightSearchOutcome.complete()}).
 */
@Component
public class FlightSearchHandler implements IntentHandler {

    private final SlotFillingService slotFilling;
    private final FlightCriteriaMapper mapper;
    private final FlightSearchService flightSearchService;
    private final ClarificationCatalog clarifications;
    private final SlotGuard slotGuard;

    public FlightSearchHandler(SlotFillingService slotFilling,
            FlightCriteriaMapper mapper,
            FlightSearchService flightSearchService,
            ClarificationCatalog clarifications,
            SlotGuard slotGuard) {
        this.slotFilling = slotFilling;
        this.mapper = mapper;
        this.flightSearchService = flightSearchService;
        this.clarifications = clarifications;
        this.slotGuard = slotGuard;
    }

    @Override
    public boolean supports(IntentType intent) {
        return intent == IntentType.FLIGHT;
    }

    @Override
    public OrchestrationResult handle(OrchestrationContext context) {
        boolean switchedDomain = !"FLIGHT".equals(context.session().getActiveDomain());
        if (switchedDomain) {
            context.session().switchDomain("FLIGHT");
        }
        
        SlotCriteria unnormalizedMerged = slotFilling.peekMerge(context.session(), context.criteria());

        // Deterministic guard over the newly extracted criteria to catch past dates and
        // invalid
        // numeric values before they are lost to normalizer logic.
        Optional<String> invalidSlot = slotGuard.checkInvalidSlots(unnormalizedMerged);
        if (invalidSlot.isPresent()) {
            return OrchestrationResult.clarify(invalidSlot.get(), "flight");
        }

        SlotCriteria merged = slotFilling.accumulate(context.session(), context.criteria());

        FlightSearchCriteria criteria = mapper.toCriteria(merged);
        FlightSearchOutcome outcome = flightSearchService.search(criteria);

        String carriedOver = TravellerCarryOver.note(switchedDomain, context.criteria(), merged);

        if (!outcome.complete()) {
            return OrchestrationResult.clarify(
                    clarifications.questionForFlight(outcome.missingFields()) + carriedOver, "flight");
        }

        // Post-search budget filter over REAL results (board type does not apply to
        // flights).
        List<Object> rawCards = new ArrayList<>(outcome.results());
        List<Object> cards = ResultFilters.applyMaxPrice(rawCards, merged.flightMaxPrice());
        cards = ResultFilters.applyDirectFlight(cards, merged.directFlight());
        cards = ResultFilters.applyDepartTimeRange(cards, merged.departTimeRange());
        cards = ResultFilters.applySort(cards, merged.sortBy());

        // A round-trip search yields every outbound+return combination the provider allows. Showing
        // them all would list the same outbound once per return, so the user picks the outbound
        // first and the returns for it are offered next (see SelectHandler).
        boolean roundTrip = cards.stream().anyMatch(RoundTripOptions::isRoundTrip);
        List<Object> combinations = roundTrip ? cards : List.of();
        if (roundTrip) {
            cards = RoundTripOptions.outboundChoices(cards);
            rawCards = new ArrayList<>(cards);
        }

        context.session().setActiveDomain("FLIGHT");
        context.session().setRoundTripOptions(new ArrayList<>(combinations));
        context.session().setPendingOutboundLegId(null);
        context.session().setLastApiResultCards(rawCards);
        context.session().setLastResultCards(cards);

        return OrchestrationResult.cards(flightReply(cards, rawCards, merged, roundTrip) + carriedOver, cards);
    }

    private String flightReply(List<Object> cards, List<Object> rawCards, SlotCriteria merged, boolean roundTrip) {
        if (!cards.isEmpty() && roundTrip) {
            // Say the price covers both legs: the same number next to a single outbound would
            // otherwise read as the price of that flight alone.
            return "Aramana uygun " + cards.size() + " gidiş uçuşu buldum (fiyatlar gidiş-dönüş "
                    + "toplamı). Önce gidişini seç:";
        }
        if (!cards.isEmpty()) {
            return "Aramana uygun " + cards.size() + " uçuş buldum:";
        }
        if (!rawCards.isEmpty() && merged.flightMaxPrice() != null) {
            String currency = merged.currency() != null ? merged.currency() : "TL";
            return merged.flightMaxPrice() + " " + currency
                    + " altında uygun uçuş bulamadım. Bütçeyi biraz artırmayı deneyebilir misin?";
        }
        return "Aradığın kriterlere uygun uçuş bulamadım. Farklı bir tarih veya güzergah deneyebilir misin?";
    }
}
