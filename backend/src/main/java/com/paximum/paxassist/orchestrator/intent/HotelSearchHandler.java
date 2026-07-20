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
import com.paximum.paxassist.orchestrator.clarify.ClarificationComposer;
import com.paximum.paxassist.orchestrator.slot.SlotGuard;
import com.paximum.paxassist.orchestrator.slot.LocationGuard;
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
    private final ClarificationComposer clarifications;
    private final SlotGuard slotGuard;

    private final LocationGuard locationGuard;

    public HotelSearchHandler(SlotFillingService slotFilling,
                              HotelCriteriaMapper mapper,
                              HotelSearchService hotelSearchService,
                              ClarificationComposer clarifications,
                              SlotGuard slotGuard,
                              LocationGuard locationGuard) {
        this.slotFilling = slotFilling;
        this.mapper = mapper;
        this.hotelSearchService = hotelSearchService;
        this.clarifications = clarifications;
        this.slotGuard = slotGuard;
        this.locationGuard = locationGuard;
    }

    @Override
    public boolean supports(IntentType intent) {
        return intent == IntentType.HOTEL;
    }

    @Override
    public OrchestrationResult handle(OrchestrationContext context) {
        boolean switchedDomain = !"HOTEL".equals(context.session().getActiveDomain());
        if (switchedDomain) {
            context.session().switchDomain("HOTEL");
        }
        
        SlotCriteria unnormalizedMerged = slotFilling.peekMerge(context.session(), context.criteria());

        // Deterministic guard over the newly extracted criteria to catch past dates and invalid 
        // numeric values before they are lost to normalizer logic.
        Optional<String> invalidSlot = slotGuard.checkInvalidSlots(unnormalizedMerged);
        if (invalidSlot.isPresent()) {
            return OrchestrationResult.clarify(invalidSlot.get(), "hotel");
        }

        Optional<String> invalidLocation = locationGuard.checkInvalidLocation(unnormalizedMerged, "HOTEL");
        if (invalidLocation.isPresent()) {
            context.session().getAccumulatedCriteria().remove("location");
            return OrchestrationResult.clarify(invalidLocation.get(), "hotel");
        }

        SlotCriteria merged = slotFilling.accumulate(context.session(), context.criteria());
        String carriedOver = TravellerCarryOver.note(switchedDomain, context.criteria(), merged);

        // Child ages are mandatory once children are present: the hotel request models each child
        // by age (there is no separate child COUNT field), so a missing/short age list would be
        // silently searched — and priced — as if childless. That is exactly the misleading price
        // Spec §3.2 forbids, so ask for the ages here instead of running the search.
        if (merged.children() != null && merged.children() > 0
                && (merged.childAges() == null || merged.childAges().size() < merged.children())) {
            return OrchestrationResult.clarify(
                    clarifications.forHotel(context.session(), context.userMessage(), List.of("childAges"))
                            + carriedOver, "hotel");
        }

        HotelSearchRequest request = mapper.toRequest(merged);
        HotelSearchResponse response = hotelSearchService.searchHotels(request);

        if ("INCOMPLETE".equals(response.status())) {
            return OrchestrationResult.clarify(
                    clarifications.forHotel(context.session(), context.userMessage(),
                            response.missingParameters()) + carriedOver, "hotel");
        }
        
        if ("INVALID_LOCATION".equals(response.status())) {
            context.session().getAccumulatedCriteria().remove("location");
            return OrchestrationResult.clarify("Girdiğin şehir/bölge (" + response.results() + ") sistemimizde bulunamadı. Lütfen geçerli bir lokasyon gir.", "hotel");
        }

        // Post-search, in-memory filters over REAL results (no fabrication): budget, board type,
        // then requested hotel features (denize sıfır / havuz / spa …) confirmed by provider data.
        List<Object> rawCards = toCards(response.results());
        List<Object> cards = ResultFilters.applyMaxPrice(rawCards, merged.hotelMaxPrice());
        cards = ResultFilters.applyBoardType(cards, merged.boardType());
        cards = ResultFilters.applyStars(cards, merged.stars(), merged.maxStars());
        List<Object> beforeFeatureFilter = cards;
        cards = ResultFilters.applyFeatures(cards, merged.features());
        cards = ResultFilters.applySort(cards, merged.sortBy());
        cards = ResultFilters.applyLimit(cards, merged.limit());

        context.session().setActiveDomain("HOTEL");
        context.session().setLastApiResultCards(rawCards);
        context.session().setLastResultCards(cards);

        return OrchestrationResult.cards(
                hotelReply(cards, rawCards, beforeFeatureFilter, merged, request.currency()) + carriedOver, cards);
    }

    private String hotelReply(List<Object> cards, List<Object> rawCards,
                              List<Object> beforeFeatureFilter, SlotCriteria merged,
                              String searchCurrency) {
        String featureLabels = ResultFilters.describeFeatures(merged.features());

        if (!cards.isEmpty()) {
            String suffix = featureLabels.isBlank() ? "" : " (" + featureLabels + ")";
            return "Aramana uygun " + cards.size() + " otel buldum" + suffix + ":";
        }
        // The feature filter emptied a non-empty list → name the unmet feature honestly rather than
        // blaming the date/city. (Only when the list had hotels before feature filtering.)
        if (!featureLabels.isBlank() && !beforeFeatureFilter.isEmpty()) {
            return featureLabels + " olarak işaretli otel bulamadım. Bu özellik için uygun sonuç "
                    + "çıkmadı; kriteri kaldırmayı ya da farklı bir bölge/tarih denemeyi ister misin?";
        }
        // Everything was filtered out by budget while the search itself had results → say so honestly.
        if (!rawCards.isEmpty() && merged.hotelMaxPrice() != null) {
            // Quote the budget in the currency the search actually ran in, not in whatever the user
            // happened to type. Since the currency is never asked for, merged.currency() is normally
            // null here — the old "TL" fallback would have told a EUR-priced search "… TL altında".
            return merged.hotelMaxPrice() + " " + searchCurrency
                    + " altında uygun otel bulamadım. Bütçeyi biraz artırmayı deneyebilir misin?";
        }
        return "Aradığın kriterlere uygun otel bulamadım. Farklı bir tarih veya şehir deneyebilir misin?";
    }

    private List<Object> toCards(Object results) {
        if (results instanceof List<?> list) {
            return new ArrayList<>(list);
        }
        return List.of();
    }
}
