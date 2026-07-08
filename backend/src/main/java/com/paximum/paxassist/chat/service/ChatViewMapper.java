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
            "destination", "hotelName", "checkIn", "checkOut", "adults", "children", "childAges",
            "rooms", "nationality", "currency", "origin", "departDate", "returnDate", "passengers",
            "tripType");

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
        return criteria.isEmpty() ? null : new PartialCriteriaDto(intent, criteria);
    }

    /** Best-effort domain guess from the persisted criteria keys (used when GET has no live domain). */
    private String inferDomain(Map<String, Object> accumulated) {
        if (accumulated.containsKey("origin") || accumulated.containsKey("departureDate")) {
            return "flight";
        }
        if (accumulated.containsKey("location") || accumulated.containsKey("checkIn")
                || accumulated.containsKey("checkOut") || accumulated.containsKey("rooms")) {
            return "hotel";
        }
        return null;
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
