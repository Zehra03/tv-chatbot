package com.paximum.paxassist.orchestrator.slot;

import java.util.Optional;

import org.springframework.stereotype.Component;

import com.paximum.paxassist.ai.SlotCriteria;
import com.paximum.paxassist.hotel.HotelSearchService;
import com.paximum.paxassist.flight.service.FlightLocationService;

/**
 * Validates location fields (hotel location, flight origin/destination) immediately
 * after they are extracted, before the system asks for other missing fields.
 */
@Component
public class LocationGuard {

    private final HotelSearchService hotelSearchService;
    private final FlightLocationService flightLocationService;

    public LocationGuard(HotelSearchService hotelSearchService, FlightLocationService flightLocationService) {
        this.hotelSearchService = hotelSearchService;
        this.flightLocationService = flightLocationService;
    }

    /**
     * Checks if the extracted locations in the criteria exist in the system.
     *
     * @param criteria the newly extracted criteria
     * @param activeDomain the current domain ("HOTEL" or "FLIGHT")
     * @return a clarification message if the location is invalid, or empty if valid/not present
     */
    public Optional<String> checkInvalidLocation(SlotCriteria criteria, String activeDomain) {
        if (criteria == null) {
            return Optional.empty();
        }

        if ("HOTEL".equalsIgnoreCase(activeDomain) && criteria.location() != null && !criteria.location().isBlank()) {
            var suggestions = hotelSearchService.suggestLocations(criteria.location());
            if (suggestions.isEmpty()) {
                return Optional.of("Girdiğin şehir/bölge (" + criteria.location() + ") sistemimizde bulunamadı. Lütfen geçerli bir lokasyon gir.");
            }
        }

        if ("FLIGHT".equalsIgnoreCase(activeDomain)) {
            if (criteria.origin() != null && !criteria.origin().isBlank()) {
                var suggestions = flightLocationService.suggest(criteria.origin(), true);
                if (suggestions.isEmpty()) {
                    return Optional.of("Girdiğin kalkış noktası (" + criteria.origin() + ") sistemimizde bulunamadı. Lütfen geçerli bir lokasyon gir.");
                }
            }
            if (criteria.destination() != null && !criteria.destination().isBlank()) {
                var suggestions = flightLocationService.suggest(criteria.destination(), false);
                if (suggestions.isEmpty()) {
                    return Optional.of("Girdiğin varış noktası (" + criteria.destination() + ") sistemimizde bulunamadı. Lütfen geçerli bir lokasyon gir.");
                }
            }
        }

        return Optional.empty();
    }
}
