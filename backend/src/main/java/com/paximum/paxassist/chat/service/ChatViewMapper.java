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

/**
 * Translates backend chat working state into the shapes the frontend expects
 * ({@code frontend/src/types/chat.ts}). Shared by {@link ChatResponseAssembler} (POST) and
 * {@link ChatSessionQueryService} (GET) so the card-typing and criteria-key mapping live in one
 * place.
 */
@Component
public class ChatViewMapper {

    private static final String HOTEL = "hotel";
    private static final String FLIGHT = "flight";

    /**
     * The {@code SlotCriteria} keys each domain owns. Chips are filtered against these so a session
     * that has searched both domains only shows the ACTIVE one's criteria — the accumulated map keeps
     * both (by design: the user may come back), but flight chips must not display leftover
     * {@code checkIn}/{@code rooms}.
     *
     * <p>Filtering is deliberately done on the SOURCE key, BEFORE {@link #KEY_RENAMES} is applied:
     * hotel's {@code location} and flight's {@code destination} both render as {@code destination},
     * so filtering after the rename cannot tell them apart and whichever the map happened to yield
     * last would win — a nondeterministic chip. Together these key sets cover exactly the frontend's
     * chip keys (see {@code CriteriaChips.tsx}); everything else is dropped.
     */
    private static final Set<String> HOTEL_SOURCE_KEYS = Set.of(
            "location", "hotelName", "checkIn", "checkOut", "nights", "rooms");

    private static final Set<String> FLIGHT_SOURCE_KEYS = Set.of(
            "origin", "destination", "departureDate", "returnDate", "passengers", "tripType");

    /** Keys both domains legitimately share, so they survive a domain switch. */
    private static final Set<String> SHARED_SOURCE_KEYS = Set.of(
            "adults", "children", "childAges", "nationality", "currency");

    /** SlotCriteria field names that differ from the frontend's criteria field names. */
    private static final Map<String, String> KEY_RENAMES = Map.of(
            "location", "destination",
            "departureDate", "departDate");

    /**
     * Types each product card ({@code "hotel"} / {@code "flight"}) to match the frontend's
     * {@code ResultCard} union — see {@link ResultCardDomain} for how a card is identified in both
     * its live and jsonb-restored shapes. Cards of an unknown type are skipped rather than guessed.
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
     * renamed/filtered {@code criteria}), showing ONLY the given domain's criteria. Returns null when
     * the domain is unknown or no criteria of that domain are filled, so the frontend simply shows
     * no chips.
     *
     * <p>The accumulated map deliberately keeps both domains' slots (a user who switches to flights
     * may come back to the hotel search), so scoping the chips to one domain is this method's job.
     *
     * @param accumulated the session's raw {@code SlotCriteria} map (may be null)
     * @param domain      "hotel"/"flight" if known, else null → inferred from the keys
     */
    public PartialCriteriaDto toPartialCriteria(Map<String, Object> accumulated, String domain) {
        if (accumulated == null || accumulated.isEmpty()) {
            return null;
        }
        String intent = (domain != null) ? domain : inferDomain(accumulated);
        // Anything other than the two product domains has no chip vocabulary on the frontend
        // (CriteriaChips renders an empty label for an unknown intent), so show nothing instead.
        if (!HOTEL.equals(intent) && !FLIGHT.equals(intent)) {
            return null;
        }
        Set<String> ownedKeys = HOTEL.equals(intent) ? HOTEL_SOURCE_KEYS : FLIGHT_SOURCE_KEYS;

        Map<String, Object> criteria = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : accumulated.entrySet()) {
            String sourceKey = entry.getKey();
            boolean belongsToDomain = ownedKeys.contains(sourceKey) || SHARED_SOURCE_KEYS.contains(sourceKey);
            if (belongsToDomain && isFilled(entry.getValue())) {
                criteria.put(KEY_RENAMES.getOrDefault(sourceKey, sourceKey), entry.getValue());
            }
        }
        return criteria.isEmpty() ? null : new PartialCriteriaDto(intent, criteria);
    }

    /**
     * Best-effort domain guess from the persisted criteria keys. Only a fallback for sessions with no
     * recorded {@code active_domain} (rows written before that column existed): it cannot tell which
     * domain the user was last in when BOTH domains' keys are present, and always answers "flight".
     */
    private String inferDomain(Map<String, Object> accumulated) {
        if (accumulated.containsKey("origin") || accumulated.containsKey("departureDate")) {
            return FLIGHT;
        }
        if (accumulated.containsKey("location") || accumulated.containsKey("checkIn")
                || accumulated.containsKey("checkOut") || accumulated.containsKey("rooms")) {
            return HOTEL;
        }
        return null;
    }

    private String productType(Object product) {
        return ResultCardDomain.productType(product);
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
