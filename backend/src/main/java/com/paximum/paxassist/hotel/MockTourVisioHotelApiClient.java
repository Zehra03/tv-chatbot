package com.paximum.paxassist.hotel;

import com.paximum.paxassist.hotel.dto.AutocompleteResponse;
import com.paximum.paxassist.hotel.dto.HotelSearchRequest;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Static hotel data for offline runs — active under the {@code mock} (CI/context tests) and
 * {@code demo} (local end-to-end with a real chat model + Flyway) profiles, so hotel search returns real
 * cards without TourVisio credentials. The real client ({@link TourVisioHotelApiClientImpl}) is
 * {@code @Profile("!mock & !demo")}, so exactly one is ever wired.
 */
@Service
@Profile("mock | demo")
public class MockTourVisioHotelApiClient implements TourVisioHotelApiClient {

    private static final List<HotelProduct> HOTELS = List.of(
            new HotelProduct("H1", "Rixos Premium", "Antalya", 5, new BigDecimal("1500.00"), "EUR", "All Inclusive", true,
                    null, List.of("Beach Hotel", "Private Beach", "Outdoor Pool", "Spa Center"), "mock-offer-h1"),
            new HotelProduct("H2", "Titanic Mardan Palace", "Antalya", 5, new BigDecimal("2000.00"), "EUR", "All Inclusive", true,
                    null, List.of("Outdoor Pool", "Water Slides", "Kids Club"), "mock-offer-h2"),
            new HotelProduct("H3", "Kaya Palazzo", "Belek", 5, new BigDecimal("1800.00"), "EUR", "All Inclusive", true,
                    null, List.of("Sand Beach", "Kids Club", "Outdoor Pool"), "mock-offer-h3"),
            new HotelProduct("H4", "Maxx Royal", "Kemer", 5, new BigDecimal("3000.00"), "EUR", "All Inclusive", true,
                    null, List.of("Beach Hotel", "Spa", "Sauna"), "mock-offer-h4"),
            new HotelProduct("H5", "Hilton Bosphorus", "Istanbul", 5, new BigDecimal("350.00"), "EUR", "Bed & Breakfast", true,
                    null, List.of("City Hotel", "Indoor Pool", "Fitness Center"), "mock-offer-h5"),
            new HotelProduct("H6", "Swissotel The Bosphorus", "Istanbul", 5, new BigDecimal("400.00"), "EUR", "Bed & Breakfast", true,
                    null, List.of("City Hotel", "Spa", "Fitness Center"), "mock-offer-h6")
    );

    @Override
    public List<HotelProduct> searchHotels(HotelSearchCriteria criteria) {
        return HOTELS;
    }

    @Override
    public String authenticate() {
        return "mock-token";
    }

    @Override
    public AutocompleteResponse getArrivalAutocomplete(String query) {
        return new AutocompleteResponse(
            new AutocompleteResponse.Header(true),
            new AutocompleteResponse.Body(List.of(
                new AutocompleteResponse.Item(
                    1,
                    new AutocompleteResponse.City("23494", "Antalya"),
                    null
                )
            ))
        );
    }

    @Override
    public Object priceSearch(HotelSearchRequest criteria, String locationId) {
        // Return real HotelProduct cards (not raw TourVisio JSON) so the whole pipeline — the
        // /api/v1/hotels/search endpoint AND the chat HOTEL handler — produces usable results offline.
        return filterByRegion(criteria != null ? criteria.destination() : null);
    }

    /**
     * A GetProductInfo-shaped sample payload (raw provider JSON structure, not HotelProduct cards) so
     * the hotel-detail feature model can be exercised end-to-end offline. Mirrors the real shape the
     * {@code HotelFeatureMapper} walks: hotel-level {@code facilities[]}, {@code themes[]} and a
     * room {@code boardName}. Facility ids match {@code facility-mapping.json}.
     */
    @Override
    public Object getProductInfo(String productId, Integer ownerProvider) {
        Map<String, Object> hotel = new java.util.LinkedHashMap<>();
        hotel.put("id", productId);
        hotel.put("name", "Mock Resort " + productId);
        hotel.put("themes", List.of(
                Map.of("id", "1", "name", "FAMILY"),
                Map.of("id", "2", "name", "BEACH"),
                Map.of("id", "3", "name", "ALL INCLUSIVE")
        ));
        hotel.put("seasons", List.of(Map.of(
                "facilityCategories", List.of(Map.of(
                        "facilities", List.of(
                                Map.of("id", "46", "name", "Pets Allowed"),
                                Map.of("id", "47", "name", "Pool"),
                                Map.of("id", "17", "name", "Indoor Pool"),
                                Map.of("id", "53", "name", "Sauna"),
                                Map.of("id", "40", "name", "Massage Center"),
                                Map.of("id", "35", "name", "Kids Club"),
                                Map.of("id", "48", "name", "Private Beach"),
                                Map.of("id", "51", "name", "Restaurant")
                        )
                ))
        )));
        hotel.put("offers", List.of(Map.of(
                "rooms", List.of(Map.of("boardName", "ALL INCLUSIVE"))
        )));
        return Map.of("body", Map.of("hotel", hotel));
    }

    /** Match the requested destination against a hotel's region (case-insensitive); all hotels when no match. */
    private List<HotelProduct> filterByRegion(String destination) {
        if (destination == null || destination.isBlank()) {
            return HOTELS;
        }
        String needle = destination.toLowerCase(Locale.ROOT).trim();
        List<HotelProduct> matches = HOTELS.stream()
                .filter(h -> h.region().toLowerCase(Locale.ROOT).contains(needle))
                .toList();
        return matches.isEmpty() ? HOTELS : matches;
    }
}
