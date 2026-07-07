package com.paximum.paxassist.orchestrator.intent;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

import com.paximum.paxassist.ai.IntentType;
import com.paximum.paxassist.ai.SlotCriteria;
import com.paximum.paxassist.hotel.HotelSearchService;
import com.paximum.paxassist.hotel.dto.HotelSearchRequest;
import com.paximum.paxassist.hotel.dto.HotelSearchResponse;
import com.paximum.paxassist.orchestrator.OrchestrationContext;
import com.paximum.paxassist.orchestrator.OrchestrationResult;
import com.paximum.paxassist.orchestrator.clarify.ClarificationCatalog;
import com.paximum.paxassist.orchestrator.mapper.HotelCriteriaMapper;
import com.paximum.paxassist.orchestrator.slot.SlotFillingService;

/**
 * Handles HOTEL intent: accumulate the newly extracted slots, map them to the hotel module's
 * request, and delegate to {@link HotelSearchService}. Completeness is decided by the hotel
 * module (single source of truth) — an {@code INCOMPLETE} status becomes a clarifying question,
 * a {@code SUCCESS} becomes result cards.
 *
 * <p>Business collaborators are injected HERE (not into the coordinator) so the orchestrator
 * stays thin.
 */
@Component
public class HotelSearchHandler implements IntentHandler {

    private final SlotFillingService slotFilling;
    private final HotelCriteriaMapper mapper;
    private final HotelSearchService hotelSearchService;
    private final ClarificationCatalog clarifications;

    public HotelSearchHandler(SlotFillingService slotFilling,
                              HotelCriteriaMapper mapper,
                              HotelSearchService hotelSearchService,
                              ClarificationCatalog clarifications) {
        this.slotFilling = slotFilling;
        this.mapper = mapper;
        this.hotelSearchService = hotelSearchService;
        this.clarifications = clarifications;
    }

    @Override
    public boolean supports(IntentType intent) {
        return intent == IntentType.HOTEL;
    }

    @Override
    public OrchestrationResult handle(OrchestrationContext context) {
        SlotCriteria merged = slotFilling.accumulate(context.session(), context.criteria());
        HotelSearchRequest request = mapper.toRequest(merged);
        HotelSearchResponse response = hotelSearchService.searchHotels(request);

        if ("INCOMPLETE".equals(response.status())) {
            return OrchestrationResult.clarify(clarifications.questionForHotel(response.missingParameters()), "hotel");
        }

        List<Object> cards = toCards(response.results());
        context.session().setActiveDomain("HOTEL");
        context.session().setLastResultCards(cards);

        String reply = cards.isEmpty()
                ? "Aradığınız kriterlere uygun otel bulamadım. Farklı bir tarih veya şehir deneyebilir misiniz?"
                : "Aramanıza uygun " + cards.size() + " otel buldum:";
        return OrchestrationResult.cards(reply, cards);
    }

    private List<Object> toCards(Object results) {
        if (results instanceof List<?> list) {
            return new ArrayList<>(list);
        }
        return List.of();
    }
}
