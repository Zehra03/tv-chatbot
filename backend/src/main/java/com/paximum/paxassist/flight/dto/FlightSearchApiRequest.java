package com.paximum.paxassist.flight.dto;

import java.time.LocalDate;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import com.paximum.paxassist.flight.domain.FlightSearchCriteria;
import com.paximum.paxassist.flight.domain.PassengerCount;
import com.paximum.paxassist.flight.domain.TripType;

/**
 * The request body {@code POST /api/v1/flights/search} receives from the frontend
 * ({@code FlightSearchCriteria} in {@code frontend/src/types/search.ts}): {@code passengers} is a
 * single count and {@code tripType} is {@code one_way}/{@code round_trip}. Frontend-only filters
 * (departTimeRange, baggage) are applied client-side and ignored here.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record FlightSearchApiRequest(
        String origin,
        String destination,
        LocalDate departDate,
        Integer passengers,
        String currency,
        String tripType,
        LocalDate returnDate,
        Boolean nonstop,
        String airline) {

    public FlightSearchCriteria toCriteria() {
        return FlightSearchCriteria.builder()
                .origin(origin)
                .destination(destination)
                .departDate(departDate)
                .returnDate(returnDate)
                .tripType(parseTripType(tripType))
                .passengers(PassengerCount.builder()
                        .adults(passengers != null ? passengers : 0)
                        .children(0)
                        .infants(0)
                        .build())
                .currency(currency)
                .nonstop(nonstop)
                .preferredAirline(airline)
                .build();
    }

    private static TripType parseTripType(String value) {
        if (value == null) {
            return null;
        }
        return switch (value) {
            case "round_trip" -> TripType.ROUND_TRIP;
            case "one_way" -> TripType.ONE_WAY;
            default -> null;
        };
    }
}
