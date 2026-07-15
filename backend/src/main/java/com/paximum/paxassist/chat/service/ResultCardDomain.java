package com.paximum.paxassist.chat.service;

import java.util.List;
import java.util.Map;

import com.paximum.paxassist.flight.domain.FlightProduct;
import com.paximum.paxassist.hotel.HotelProduct;

/**
 * Reads the product kind off a result card. A card is a live product ({@link HotelProduct} /
 * {@link FlightProduct}) on the turn it is produced, but a generic {@code Map} once it has been
 * through the {@code result_cards} jsonb column — so every reader has to handle both shapes.
 *
 * <p>Kept here rather than in {@link ChatViewMapper} because two collaborators need it for different
 * reasons: the mapper types cards for the frontend, and {@link JpaChatSessionStore} needs to know
 * which domain box a restored card list belongs in.
 */
final class ResultCardDomain {

    /** Frontend {@code ResultCard} union wording (lower case). */
    static final String HOTEL_TYPE = "hotel";
    static final String FLIGHT_TYPE = "flight";

    /** Session/DB wording — matches {@code chat_sessions.active_domain} and its CHECK constraint. */
    static final String HOTEL_DOMAIN = "HOTEL";
    static final String FLIGHT_DOMAIN = "FLIGHT";

    private ResultCardDomain() {
    }

    /**
     * The card's product type, or null when it cannot be identified — an unknown card is skipped
     * rather than guessed at. Hotel and flight field sets are disjoint, so for a jsonb-restored map
     * key presence identifies the type.
     */
    static String productType(Object card) {
        if (card instanceof HotelProduct) {
            return HOTEL_TYPE;
        }
        if (card instanceof FlightProduct) {
            return FLIGHT_TYPE;
        }
        if (card instanceof Map<?, ?> map) {
            if (map.containsKey("productType") && map.get("productType") != null) {
                return map.get("productType").toString();
            }
            if (map.containsKey("hotelName") || map.containsKey("boardType") || map.containsKey("stars")) {
                return HOTEL_TYPE;
            }
            if (map.containsKey("airline") || map.containsKey("flightNumber") || map.containsKey("departTime")) {
                return FLIGHT_TYPE;
            }
        }
        return null;
    }

    /**
     * The domain box a card list belongs in, or null when no card in it can be typed. A list comes
     * from a single search and is therefore homogeneous, so the first identifiable card decides.
     */
    static String domainOf(List<Object> cards) {
        if (cards == null) {
            return null;
        }
        for (Object card : cards) {
            String type = productType(card);
            if (HOTEL_TYPE.equals(type)) {
                return HOTEL_DOMAIN;
            }
            if (FLIGHT_TYPE.equals(type)) {
                return FLIGHT_DOMAIN;
            }
        }
        return null;
    }
}
