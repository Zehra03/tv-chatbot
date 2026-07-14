package com.paximum.paxassist.flight.dto;

import java.time.LocalDate;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import com.paximum.paxassist.flight.domain.FlightSearchCriteria;
import com.paximum.paxassist.flight.domain.PassengerCount;
import com.paximum.paxassist.flight.domain.TripType;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * The request body {@code POST /api/v1/flights/search} receives from the frontend
 * ({@code FlightSearchCriteria} in {@code frontend/src/types/search.ts}): {@code passengers} is a
 * single count and {@code tripType} is {@code one_way}/{@code round_trip}. Frontend-only filters
 * (departTimeRange, baggage) are applied client-side and ignored here.
 *
 * <p>Unlike the chat path — where {@code TravelDateGuard} and slot-filling vet the criteria — this
 * REST boundary is reached directly by the search form, so it carries its own Bean Validation:
 * the fields a search cannot run without must be present, dates must not be in the past, currency
 * must be a 3-letter code, and origin/destination must differ. {@code @Valid} on the controller turns
 * a violation into a 400 via {@code FlightExceptionHandler}.
 *
 * <p>The presence constraints are what make the value constraints bite: Bean Validation skips a null
 * value, so {@code @Min(1)} alone lets {@code passengers: null} through — and {@code toCriteria()}
 * would then send a zero-passenger search to TourVisio.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record FlightSearchApiRequest(
        @NotBlank(message = "origin is required") String origin,
        @NotBlank(message = "destination is required") String destination,
        @NotNull(message = "departDate is required")
        @FutureOrPresent(message = "departDate must not be in the past") LocalDate departDate,
        @NotNull(message = "passengers is required")
        @Min(value = 1, message = "passengers must be at least 1") Integer passengers,
        @Size(min = 3, max = 3, message = "currency must be a 3-letter code") String currency,
        String tripType,
        @FutureOrPresent(message = "returnDate must not be in the past") LocalDate returnDate,
        Boolean nonstop,
        String airline) {

    /** Cross-field: a round trip's return leg cannot depart before the outbound leg. */
    @AssertTrue(message = "returnDate must be on or after departDate")
    private boolean isReturnDateNotBeforeDepartDate() {
        return departDate == null || returnDate == null || !returnDate.isBefore(departDate);
    }

    /** Cross-field: searching from a place to itself is nonsensical and never yields flights. */
    @AssertTrue(message = "origin and destination must differ")
    private boolean isOriginDifferentFromDestination() {
        if (origin == null || destination == null || origin.isBlank() || destination.isBlank()) {
            return true;
        }
        return !origin.trim().equalsIgnoreCase(destination.trim());
    }

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
