package com.paximum.paxassist.orchestrator.intent;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Component;

import com.paximum.paxassist.ai.IntentType;
import com.paximum.paxassist.ai.SlotCriteria;
import com.paximum.paxassist.hotel.HotelSearchService;
import com.paximum.paxassist.hotel.dto.HotelSearchRequest;
import com.paximum.paxassist.hotel.dto.HotelSearchResponse;
import com.paximum.paxassist.orchestrator.OrchestrationContext;
import com.paximum.paxassist.orchestrator.OrchestrationResult;
import com.paximum.paxassist.orchestrator.clarify.ClarificationCatalog;
import com.paximum.paxassist.orchestrator.slot.SlotGuard;
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
    private final SlotGuard slotGuard;

    public HotelSearchHandler(SlotFillingService slotFilling,
                              HotelCriteriaMapper mapper,
                              HotelSearchService hotelSearchService,
                              ClarificationCatalog clarifications,
                              SlotGuard slotGuard) {
        this.slotFilling = slotFilling;
        this.mapper = mapper;
        this.hotelSearchService = hotelSearchService;
        this.clarifications = clarifications;
        this.slotGuard = slotGuard;
    }

    @Override
    public boolean supports(IntentType intent) {
        return intent == IntentType.HOTEL;
    }

    @Override
    public OrchestrationResult handle(OrchestrationContext context) {
        SlotCriteria merged = slotFilling.accumulate(context.session(), context.criteria());

        // Deterministic guard over the newly extracted criteria to catch past dates and invalid 
        // numeric values before they are lost to normalizer logic.
        Optional<String> invalidSlot = slotGuard.checkInvalidSlots(context.criteria());
        if (invalidSlot.isPresent()) {
            return OrchestrationResult.clarify(invalidSlot.get(), "hotel");
        }

        HotelSearchRequest request = mapper.toRequest(merged);
        HotelSearchResponse response = hotelSearchService.searchHotels(request);

        if ("INCOMPLETE".equals(response.status())) {
            return OrchestrationResult.clarify(clarifications.questionForHotel(response.missingParameters()), "hotel");
        }

        // Post-search, in-memory filters over REAL results (no fabrication): budget, board type,
        // then requested hotel features (denize sıfır / havuz / spa …) confirmed by provider data.
        List<Object> rawCards = toCards(response.results());
        List<Object> cards = ResultFilters.applyMaxPrice(rawCards, merged.hotelMaxPrice());
        cards = ResultFilters.applyBoardType(cards, merged.boardType());
        List<Object> beforeFeatureFilter = cards;
        cards = ResultFilters.applyFeatures(cards, merged.features());

        context.session().setActiveDomain("HOTEL");
        context.session().setLastResultCards(cards);

        return OrchestrationResult.cards(hotelReply(cards, rawCards, beforeFeatureFilter, merged), cards);
    }

    private String hotelReply(List<Object> cards, List<Object> rawCards,
                              List<Object> beforeFeatureFilter, SlotCriteria merged) {
        String featureLabels = ResultFilters.describeFeatures(merged.features());

        if (!cards.isEmpty()) {
            String suffix = featureLabels.isBlank() ? "" : " (" + featureLabels + ")";
            return "Aramanıza uygun " + cards.size() + " otel buldum" + suffix + ":";
        }
        // The feature filter emptied a non-empty list → name the unmet feature honestly rather than
        // blaming the date/city. (Only when the list had hotels before feature filtering.)
        if (!featureLabels.isBlank() && !beforeFeatureFilter.isEmpty()) {
            return featureLabels + " olarak işaretli otel bulamadım. Bu özellik için uygun sonuç "
                    + "çıkmadı; kriteri kaldırmayı ya da farklı bir bölge/tarih denemeyi ister misiniz?";
        }
        // Everything was filtered out by budget while the search itself had results → say so honestly.
        if (!rawCards.isEmpty() && merged.hotelMaxPrice() != null) {
            String currency = merged.currency() != null ? merged.currency() : "TL";
            return merged.hotelMaxPrice() + " " + currency
                    + " altında uygun otel bulamadım. Bütçeyi biraz artırmayı deneyebilir misiniz?";
        }
        return "Aradığınız kriterlere uygun otel bulamadım. Farklı bir tarih veya şehir deneyebilir misiniz?";
    }

    private List<Object> toCards(Object results) {
        if (results instanceof List<?> list) {
            return new ArrayList<>(list);
        }
        return List.of();
    }
}
