package com.paximum.paxassist.orchestrator.intent;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

import com.paximum.paxassist.ai.IntentType;
import com.paximum.paxassist.ai.SlotCriteria;
import com.paximum.paxassist.flight.domain.FlightSearchCriteria;
import com.paximum.paxassist.flight.service.FlightSearchOutcome;
import com.paximum.paxassist.flight.service.FlightSearchService;
import com.paximum.paxassist.orchestrator.OrchestrationContext;
import com.paximum.paxassist.orchestrator.OrchestrationResult;
import com.paximum.paxassist.orchestrator.clarify.ClarificationCatalog;
import com.paximum.paxassist.orchestrator.mapper.FlightCriteriaMapper;
import com.paximum.paxassist.orchestrator.slot.SlotFillingService;

/**
 * Handles FLIGHT intent — the mirror of {@link HotelSearchHandler} for the flight module.
 * Completeness is decided by {@link FlightSearchService} ({@code FlightSearchOutcome.complete()}).
 */
@Component
public class FlightSearchHandler implements IntentHandler {

    private final SlotFillingService slotFilling;
    private final FlightCriteriaMapper mapper;
    private final FlightSearchService flightSearchService;
    private final ClarificationCatalog clarifications;

    public FlightSearchHandler(SlotFillingService slotFilling,
                               FlightCriteriaMapper mapper,
                               FlightSearchService flightSearchService,
                               ClarificationCatalog clarifications) {
        this.slotFilling = slotFilling;
        this.mapper = mapper;
        this.flightSearchService = flightSearchService;
        this.clarifications = clarifications;
    }

    @Override
    public boolean supports(IntentType intent) {
        return intent == IntentType.FLIGHT;
    }

    @Override
    public OrchestrationResult handle(OrchestrationContext context) {
        SlotCriteria merged = slotFilling.accumulate(context.session(), context.criteria());
        FlightSearchCriteria criteria = mapper.toCriteria(merged);
        FlightSearchOutcome outcome = flightSearchService.search(criteria);

        if (!outcome.complete()) {
            return OrchestrationResult.clarify(clarifications.questionForFlight(outcome.missingFields()), "flight");
        }

        List<Object> cards = new ArrayList<>(outcome.results());
        context.session().setActiveDomain("FLIGHT");
        context.session().setLastResultCards(cards);

        String reply = cards.isEmpty()
                ? "Aradığınız kriterlere uygun uçuş bulamadım. Farklı bir tarih veya güzergah deneyebilir misiniz?"
                : "Aramanıza uygun " + cards.size() + " uçuş buldum:";
        return OrchestrationResult.cards(reply, cards);
    }
}
