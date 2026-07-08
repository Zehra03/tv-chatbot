package com.paximum.paxassist.hotel;

import com.paximum.paxassist.hotel.dto.AutocompleteResponse;
import com.paximum.paxassist.hotel.dto.HotelSearchRequest;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;

/**
 * Static hotel data for offline runs — active under the {@code mock} (CI/context tests) and
 * {@code demo} (local end-to-end with Ollama + Flyway) profiles, so hotel search returns real
 * cards without TourVisio credentials. The real client ({@link TourVisioHotelApiClientImpl}) is
 * {@code @Profile("!mock & !demo")}, so exactly one is ever wired.
 */
@Service
@Profile("mock | demo")
public class MockTourVisioHotelApiClient implements TourVisioHotelApiClient {

    private static final List<HotelProduct> HOTELS = List.of(
            new HotelProduct("H1", "Rixos Premium", "Antalya", 5, new BigDecimal("1500.00"), "EUR", "All Inclusive", true),
            new HotelProduct("H2", "Titanic Mardan Palace", "Antalya", 5, new BigDecimal("2000.00"), "EUR", "All Inclusive", true),
            new HotelProduct("H3", "Kaya Palazzo", "Belek", 5, new BigDecimal("1800.00"), "EUR", "All Inclusive", true),
            new HotelProduct("H4", "Maxx Royal", "Kemer", 5, new BigDecimal("3000.00"), "EUR", "All Inclusive", true),
            new HotelProduct("H5", "Hilton Bosphorus", "Istanbul", 5, new BigDecimal("350.00"), "EUR", "Bed & Breakfast", true),
            new HotelProduct("H6", "Swissotel The Bosphorus", "Istanbul", 5, new BigDecimal("400.00"), "EUR", "Bed & Breakfast", true)
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
