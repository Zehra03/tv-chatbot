package com.paximum.paxassist.chat.service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.paximum.paxassist.chat.dto.PartialCriteriaDto;
import com.paximum.paxassist.chat.dto.ResultCardDto;
import com.paximum.paxassist.flight.domain.FlightProduct;
import com.paximum.paxassist.hotel.HotelProduct;

/**
 * Translates backend chat working state into the shapes the frontend expects
 * ({@code frontend/src/types/chat.ts}). Shared by {@link ChatResponseAssembler} (POST) and
 * {@link ChatSessionQueryService} (GET) so the card-typing and criteria-key mapping live in one
 * place.
 */
@Component
public class ChatViewMapper {

    /** Frontend criteria keys (see {@code CriteriaChips.tsx}); everything else is dropped. */
    private static final Set<String> KNOWN_KEYS = Set.of(
            "destination", "hotelName", "checkIn", "checkOut", "nights", "adults", "children",
            "childAges", "rooms", "nationality", "currency", "origin", "departDate", "returnDate",
            "passengers", "tripType");

    /** SlotCriteria field names that differ from the frontend's criteria field names. */
    private static final Map<String, String> KEY_RENAMES = Map.of(
            "location", "destination",
            "departureDate", "departDate");

    /**
     * Types each product card by its runtime class — {@link HotelProduct} → {@code "hotel"},
     * {@link FlightProduct} → {@code "flight"} — matching the frontend's {@code ResultCard} union.
     * Cards of an unknown type are skipped rather than guessed.
     */
    public List<ResultCardDto> toResultCards(List<Object> products) {
        if (products == null || products.isEmpty()) {
            return List.of();
        }
        List<ResultCardDto> cards = new ArrayList<>(products.size());
        for (Object product : products) {
            String type = productType(product);
            if (type != null) {
                cards.add(new ResultCardDto(type, product));
            }
        }
        return cards;
    }

    /**
     * Wraps the accumulated slot map as the frontend {@code PartialCriteria} ({@code intent} +
     * renamed/filtered {@code criteria}). Returns null when the domain is unknown or no known
     * criteria are filled, so the frontend simply shows no chips.
     *
     * @param accumulated the session's raw {@code SlotCriteria} map (may be null)
     * @param domain      "hotel"/"flight" if known this turn, else null → inferred from the keys
     */
    public PartialCriteriaDto toPartialCriteria(Map<String, Object> accumulated, String domain) {
        if (accumulated == null || accumulated.isEmpty()) {
            return null;
        }
        String intent = (domain != null) ? domain : inferDomain(accumulated);
        if (intent == null) {
            return null;
        }
        Map<String, Object> criteria = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : accumulated.entrySet()) {
            String key = KEY_RENAMES.getOrDefault(entry.getKey(), entry.getKey());
            if (KNOWN_KEYS.contains(key) && isFilled(entry.getValue())) {
                criteria.put(key, entry.getValue());
            }
        }
        if ("flight".equals(intent)) {
            foldFlightPassengers(criteria);
        }
        return criteria.isEmpty() ? null : new PartialCriteriaDto(intent, criteria);
    }

    /**
     * Collapses the slot map's {@code adults}/{@code children} pair into the single {@code passengers}
     * count the frontend's flight criteria carry ({@code FlightSearchCriteria} in
     * {@code frontend/src/types/search.ts} has no adults/children — the flight search form posts one
     * number). Their SUM is the right value: {@code FlightCriteriaMapper} sends both to the provider,
     * so the returned offer is priced for that many seats.
     *
     * <p>Without this fold the frontend read {@code passengers} as undefined and
     * {@code buildFlightDraft} fell back to 1, so a 2-passenger chat search opened ONE traveller row
     * in the reservation form — which TourVisio rejects for not matching the offer's pax. The search
     * itself was always correct; only this view of the criteria was wrong.
     *
     * <p>{@code childAges} is dropped for the same reason (no such field on the flight type). Note the
     * reservation form types every flight traveller row as an adult — a pre-existing limit of the
     * flight booking model (the REST path always posts {@code children: 0}); what matters to TourVisio
     * is that the row COUNT matches the offer.
     */
    private void foldFlightPassengers(Map<String, Object> criteria) {
        Integer adults = asCount(criteria.remove("adults"));
        Integer children = asCount(criteria.remove("children"));
        criteria.remove("childAges");
        if (adults == null && children == null) {
            return;
        }
        int total = (adults == null ? 0 : adults) + (children == null ? 0 : children);
        if (total > 0) {
            criteria.put("passengers", total);
        }
    }

    /** Slot values survive a jsonb round-trip on GET, so a count can come back as any {@link Number}. */
    private Integer asCount(Object value) {
        return (value instanceof Number number) ? number.intValue() : null;
    }

    /** SlotCriteria fields that only a hotel search fills. */
    private static final List<String> HOTEL_SIGNALS = List.of(
            "location", "checkIn", "checkOut", "nights", "rooms", "stars", "maxStars", "boardType",
            "features", "hotelMaxPrice");

    /** SlotCriteria fields that only a flight search fills. */
    private static final List<String> FLIGHT_SIGNALS = List.of(
            "origin", "destination", "departureDate", "returnDate", "cabinClass", "flightMaxPrice",
            "directFlight", "airline", "departTimeRange");

    /**
     * Best-effort domain guess from the persisted criteria (used when GET has no live domain).
     *
     * <p>Weighs FILLED values, not key presence: {@code SlotFillingService.accumulate} stores the
     * whole {@code SlotCriteria} record via {@code convertValue}, and the record carries no
     * {@code @JsonInclude(NON_NULL)} — so every one of its fields is present in the map, null ones
     * included. A {@code containsKey} test therefore matched on every session and typed pure hotel
     * searches as "flight". Shared fields (adults, currency, …) are deliberately not signals.
     * A tie means genuinely mixed state → null, so the caller shows no chips rather than a wrong one.
     */
    private String inferDomain(Map<String, Object> accumulated) {
        int hotel = countFilled(accumulated, HOTEL_SIGNALS);
        int flight = countFilled(accumulated, FLIGHT_SIGNALS);
        if (hotel > flight) {
            return "hotel";
        }
        if (flight > hotel) {
            return "flight";
        }
        return null;
    }

    private int countFilled(Map<String, Object> accumulated, List<String> keys) {
        int filled = 0;
        for (String key : keys) {
            if (isFilled(accumulated.get(key))) {
                filled++;
            }
        }
        return filled;
    }

    /**
     * Types a card whether it is a live product (POST, this turn) or a jsonb-restored generic map
     * (GET transcript). Hotel and flight field sets are disjoint, so key presence identifies the type.
     */
    private String productType(Object product) {
        if (product instanceof HotelProduct) {
            return "hotel";
        }
        if (product instanceof FlightProduct) {
            return "flight";
        }
        if (product instanceof Map<?, ?> map) {
            if (map.containsKey("productType") && map.get("productType") != null) {
                return map.get("productType").toString();
            }
            if (map.containsKey("hotelName") || map.containsKey("boardType") || map.containsKey("stars")) {
                return "hotel";
            }
            if (map.containsKey("airline") || map.containsKey("flightNumber") || map.containsKey("departTime")) {
                return "flight";
            }
        }
        return null;
    }

    private boolean isFilled(Object value) {
        if (value == null) {
            return false;
        }
        if (value instanceof String s) {
            return !s.isBlank();
        }
        if (value instanceof Collection<?> c) {
            return !c.isEmpty();
        }
        return true;
    }
}
