package com.paximum.paxassist.orchestrator.intent;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Component;

import com.paximum.paxassist.ai.IntentType;
import com.paximum.paxassist.ai.SlotCriteria;
import com.paximum.paxassist.flight.domain.FlightSearchCriteria;
import com.paximum.paxassist.flight.domain.PassengerCount;
import com.paximum.paxassist.flight.service.FlightSearchOutcome;
import com.paximum.paxassist.flight.service.FlightSearchService;
import com.paximum.paxassist.orchestrator.OrchestrationContext;
import com.paximum.paxassist.orchestrator.OrchestrationResult;
import com.paximum.paxassist.orchestrator.clarify.ClarificationComposer;
import com.paximum.paxassist.orchestrator.slot.SlotGuard;
import com.paximum.paxassist.orchestrator.slot.LocationGuard;
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
    private final ClarificationComposer clarifications;
    private final SlotGuard slotGuard;

    private final LocationGuard locationGuard;

    public FlightSearchHandler(SlotFillingService slotFilling,
            FlightCriteriaMapper mapper,
            FlightSearchService flightSearchService,
            ClarificationCatalog clarifications,
            SlotGuard slotGuard,
            LocationGuard locationGuard) {
            ClarificationComposer clarifications,
            SlotGuard slotGuard) {
        this.slotFilling = slotFilling;
        this.mapper = mapper;
        this.flightSearchService = flightSearchService;
        this.clarifications = clarifications;
        this.slotGuard = slotGuard;
        this.locationGuard = locationGuard;
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

        Optional<String> invalidLocation = locationGuard.checkInvalidLocation(unnormalizedMerged, "FLIGHT");
        if (invalidLocation.isPresent()) {
            context.session().getAccumulatedCriteria().remove("origin");
            context.session().getAccumulatedCriteria().remove("destination");
            return OrchestrationResult.clarify(invalidLocation.get(), "flight");
        }

        SlotCriteria merged = slotFilling.accumulate(context.session(), context.criteria());

        String carriedOver = TravellerCarryOver.note(switchedDomain, context.criteria(), merged);

        // An accompanying child's fare depends on its age: under 2 it flies as a lap infant, from 12
        // it pays the adult fare (see FlightCriteriaMapper). A bare count cannot be typed, so without
        // the ages the search would price every child as a "child" — a quote the traveller cannot
        // book at. Ask instead of guessing; the hotel side asks for the same reason.
        if (merged.children() != null && merged.children() > 0
                && (merged.childAges() == null || merged.childAges().size() < merged.children())) {
            return OrchestrationResult.clarify(
                    clarifications.forFlight(context.session(), context.userMessage(), List.of("childAges"))
                            + carriedOver, "flight");
        }

        FlightSearchCriteria criteria = mapper.toCriteria(merged);

        // Party-size rules the provider enforces anyway, but only after a booking is under way. The
        // ages have been resolved by now, so this is the first point the real seat/lap counts exist.
        Optional<String> partyProblem = partyLimitMessage(criteria.getPassengers());
        if (partyProblem.isPresent()) {
            return OrchestrationResult.clarify(partyProblem.get() + carriedOver, "flight");
        }

        FlightSearchOutcome outcome = flightSearchService.search(criteria);

        if (!outcome.complete()) {
            return OrchestrationResult.clarify(
                    clarifications.forFlight(context.session(), context.userMessage(), outcome.missingFields())
                            + carriedOver, "flight");
        }

        // Post-search budget filter over REAL results (board type does not apply to
        // flights).
        List<Object> rawCards = new ArrayList<>(outcome.results());
        List<Object> cards = ResultFilters.applyMaxPrice(rawCards, merged.flightMaxPrice());
        cards = ResultFilters.applyDirectFlight(cards, merged.directFlight());
        cards = ResultFilters.applyDepartTimeRange(cards, merged.departTimeRange());
        cards = ResultFilters.applySort(cards, merged.sortBy());

        // When the provider tokens the legs separately, a round-trip search yields every allowed
        // outbound+return combination. Showing them all would list the same outbound once per
        // return, so the user picks the outbound first and the returns for it come next (see
        // SelectHandler). A trip sold as one ticket has no such choice — each card is already the
        // whole trip, so it is listed as-is and its Seç goes to the reservation.
        boolean paired = cards.stream().anyMatch(RoundTripOptions::isPairedCombination);
        List<Object> combinations = paired ? cards : List.of();
        if (paired) {
            cards = RoundTripOptions.outboundChoices(cards);
            rawCards = new ArrayList<>(cards);
        }
        boolean returnLegShown = cards.stream().anyMatch(RoundTripOptions::hasReturnLeg);

        context.session().setActiveDomain("FLIGHT");
        context.session().setRoundTripOptions(new ArrayList<>(combinations));
        context.session().setPendingOutboundLegId(null);
        context.session().setLastApiResultCards(rawCards);
        context.session().setLastResultCards(cards);

        return OrchestrationResult.cards(
                flightReply(cards, rawCards, merged, paired, returnLegShown, criteria.getCurrency())
                        + carriedOver, cards);
    }

    /**
     * Turns the flight domain's party-size rules into the wording the chat uses. The rules live on
     * {@link PassengerCount} (they are the provider's, and the REST/MCP surfaces enforce the same
     * ones); only the Turkish phrasing belongs here.
     */
    private Optional<String> partyLimitMessage(PassengerCount passengers) {
        if (passengers == null) {
            return Optional.empty();
        }
        if (passengers.exceedsSeatLimit()) {
            return Optional.of("Tek aramada en fazla " + PassengerCount.MAX_SEATS + " koltuklu yolcu "
                    + "arayabiliyorum (kucakta uçan bebekler sayılmaz), sen " + passengers.seatedCount()
                    + " kişi belirttin. Grubu bölüp ayrı ayrı aramayı deneyebilir misin?");
        }
        if (passengers.hasMoreInfantsThanAdults()) {
            return Optional.of("Her bebek bir yetişkinin kucağında uçtuğu için bebek sayısı yetişkin "
                    + "sayısını geçemez (" + passengers.getInfants() + " bebek, " + passengers.getAdults()
                    + " yetişkin). Yetişkin sayısını artırmak ister misin?");
        }
        return Optional.empty();
    }

    private String flightReply(List<Object> cards, List<Object> rawCards, SlotCriteria merged,
                               boolean paired, boolean returnLegShown, String searchCurrency) {
        if (!cards.isEmpty() && paired) {
            // Say the price covers both legs: the same number next to a single outbound would
            // otherwise read as the price of that flight alone.
            return "Aramana uygun " + cards.size() + " gidiş uçuşu buldum (fiyatlar gidiş-dönüş "
                    + "toplamı). Önce gidişini seç:";
        }
        if (!cards.isEmpty() && returnLegShown) {
            // Whole trips, sold as one ticket: nothing to choose between the legs, so do not ask —
            // promising a step that cannot follow is what made "Önce gidişini seç" a lie when the
            // card's Seç went straight to the reservation.
            return "Aramana uygun " + cards.size() + " gidiş-dönüş buldum (fiyatlar gidiş-dönüş "
                    + "toplamı, tek bilet):";
        }
        if (!cards.isEmpty()) {
            return "Aramana uygun " + cards.size() + " uçuş buldum:";
        }
        if (!rawCards.isEmpty() && merged.flightMaxPrice() != null) {
            // The currency the search ran in — not merged.currency(), which is normally null since
            // the user is never asked. See the same fix in HotelSearchHandler.
            return merged.flightMaxPrice() + " " + searchCurrency
                    + " altında uygun uçuş bulamadım. Bütçeyi biraz artırmayı deneyebilir misin?";
        }
        // The baggage request narrowed the fares inside the search itself, so an empty list here means
        // no fare met it — name that instead of blaming the route/date the user did give us.
        String baggageAsk = describeBaggageAsk(merged);
        if (baggageAsk != null) {
            return baggageAsk + " bir uçuş bulamadım. Bagaj koşulunu gevşetmeyi ya da farklı bir "
                    + "tarih denemeyi ister misin?";
        }
        return "Aradığın kriterlere uygun uçuş bulamadım. Farklı bir tarih veya güzergah deneyebilir misin?";
    }

    /** How the user's baggage request reads back to them, or null when they made none. */
    private String describeBaggageAsk(SlotCriteria merged) {
        if (merged.minCheckedBaggageKg() != null) {
            return merged.minCheckedBaggageKg() + " kg ve üzeri kayıtlı bagaj içeren";
        }
        if (Boolean.TRUE.equals(merged.checkedBaggage())) {
            return "kayıtlı bagaj içeren";
        }
        if (Boolean.FALSE.equals(merged.checkedBaggage())) {
            return "kayıtlı bagaj içermeyen";
        }
        return null;
    }
}
