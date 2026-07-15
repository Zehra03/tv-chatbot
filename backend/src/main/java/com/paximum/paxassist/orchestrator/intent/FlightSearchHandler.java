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
        if (!"FLIGHT".equals(context.session().getActiveDomain())) {
            if (context.session().getAccumulatedCriteria() != null) {
                context.session().getAccumulatedCriteria().clear();
            }
            context.session().setActiveDomain("FLIGHT");
        }
        
        SlotCriteria merged = slotFilling.accumulate(context.session(), context.criteria());

        // Deterministic guard over the newly extracted criteria to catch past dates and
        // invalid
        // numeric values before they are lost to normalizer logic.
        Optional<String> invalidSlot = slotGuard.checkInvalidSlots(context.criteria());
        if (invalidSlot.isPresent()) {
            return OrchestrationResult.clarify(invalidSlot.get(), "flight");
        }

        FlightSearchCriteria criteria = mapper.toCriteria(merged);
        FlightSearchOutcome outcome = flightSearchService.search(criteria);

        if (!outcome.complete()) {
            return OrchestrationResult.clarify(clarifications.questionForFlight(outcome.missingFields()), "flight");
        }

        // Post-search budget filter over REAL results (board type does not apply to
        // flights).
        List<Object> rawCards = new ArrayList<>(outcome.results());
        List<Object> cards = ResultFilters.applyMaxPrice(rawCards, merged.flightMaxPrice());
        cards = ResultFilters.applyDirectFlight(cards, merged.directFlight());
        cards = ResultFilters.applySort(cards, merged.sortBy());

        context.session().setActiveDomain("FLIGHT");
        context.session().setLastApiResultCards(rawCards);
        context.session().setLastResultCards(cards);

        return OrchestrationResult.cards(flightReply(cards, rawCards, merged), cards);
    }

    private String flightReply(List<Object> cards, List<Object> rawCards, SlotCriteria merged) {
        if (!cards.isEmpty()) {
            return "Aramanıza uygun " + cards.size() + " uçuş buldum:";
        }
        if (!rawCards.isEmpty() && merged.flightMaxPrice() != null) {
            String currency = merged.currency() != null ? merged.currency() : "TL";
            return merged.flightMaxPrice() + " " + currency
                    + " altında uygun uçuş bulamadım. Bütçeyi biraz artırmayı deneyebilir misiniz?";
        }
        return "Aradığınız kriterlere uygun uçuş bulamadım. Farklı bir tarih veya güzergah deneyebilir misiniz?";
    }
}
