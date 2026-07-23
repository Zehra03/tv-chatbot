package com.paximum.paxassist.flight.dto;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import com.paximum.paxassist.flight.domain.FlightSearchCriteria;
import com.paximum.paxassist.flight.domain.PassengerCount;
import com.paximum.paxassist.flight.domain.TripType;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/**
 * The request body {@code POST /api/v1/flights/search} receives from the frontend
 * ({@code FlightSearchCriteria} in {@code frontend/src/types/search.ts}) and {@code tripType} is
 * {@code one_way}/{@code round_trip}. {@code departTimeRange} is honoured server-side —
 * {@code toCriteria()} maps it onto the criteria and {@code FlightResultFilter} applies it to the
 * results. {@code baggage} stays a client-side filter.
 *
 * <p><b>HATA 5 (fixed):</b> the manual search form used to send a single {@code passengers} count,
 * which {@code toCriteria()} then booked entirely as adults — {@code children}/{@code infants} were
 * hardcoded to zero even though TourVisio prices them differently and the chat/MCP search paths
 * already differentiate passenger type ({@code FlightCriteriaMapper}, {@code HotelFlightTools}).
 * This boundary now takes the same two inputs those paths do — {@link #adults()} and
 * {@link #childAges()} — and types each accompanying child by age via
 * {@link PassengerCount#ofChildAges}, the one place that rule lives (0–2 flies as an infant, 3–11 as
 * a child, 12+ pays the adult fare — see {@link PassengerCount}).
 *
 * <p>Unlike the chat path — where {@code TravelDateGuard} and slot-filling vet the criteria — this
 * REST boundary is reached directly by the search form, so it carries its own Bean Validation:
 * the fields a search cannot run without must be present, dates must not be in the past, currency
 * must be a 3-letter code, origin/destination must differ, and the party must fit one booking
 * ({@link PassengerCount#MAX_SEATS} seats, no more infants than adults — the same two rules
 * {@code FlightSearchHandler} enforces on the chat path). {@code @Valid} on the controller turns
 * a violation into a 400 via {@code FlightExceptionHandler}.
 *
 * <p>The presence constraints are what make the value constraints bite: Bean Validation skips a null
 * value, so {@code @Min(1)} alone lets {@code adults: null} through — and {@code toCriteria()}
 * would then send a zero-passenger search to TourVisio.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record FlightSearchApiRequest(
        @NotBlank(message = "origin is required") String origin,
        @NotBlank(message = "destination is required") String destination,
        @NotNull(message = "departDate is required")
        @FutureOrPresent(message = "departDate must not be in the past") LocalDate departDate,
        @NotNull(message = "adults is required")
        @Min(value = 1, message = "adults must be at least 1") Integer adults,
        /**
         * One entry per accompanying child, each a birth-year age; empty/absent means no children.
         * {@link PassengerCount#ofChildAges} types each by age, so this is the only input the form
         * needs to differentiate child and infant fares — no separate counts to keep in sync.
         */
        List<@PositiveOrZero @Max(MAX_CHILD_AGE) Integer> childAges,
        @Size(min = 3, max = 3, message = "currency must be a 3-letter code") String currency,
        String tripType,
        @FutureOrPresent(message = "returnDate must not be in the past") LocalDate returnDate,
        Boolean nonstop,
        String airline,
        @Valid TimeRange departTimeRange) {

    /** Oldest age still sent as a "child" here; {@link PassengerCount#ofChildAges} does the actual fare typing. */
    private static final int MAX_CHILD_AGE = 17;

    /**
     * Departure-time window from the search form ({@code TimeRange} in {@code frontend/src/types/search.ts}),
     * as "HH:mm" strings. Both bounds are optional and inclusive: only {@code from} means "at or after".
     */
    public record TimeRange(
            @Pattern(regexp = TIME_PATTERN, message = "from must be a HH:mm time") String from,
            @Pattern(regexp = TIME_PATTERN, message = "to must be a HH:mm time") String to) {

        /** Cross-field: an inverted window ("from 18:00 to 06:00") would match nothing — reject it. */
        @AssertTrue(message = "departTimeRange.from must be at or before departTimeRange.to")
        private boolean isRangeOrdered() {
            LocalTime start = parseTime(from);
            LocalTime end = parseTime(to);
            return start == null || end == null || !start.isAfter(end);
        }
    }

    /** 24-hour "HH:mm"; anchored so "9:00" or "25:00" is a 400 rather than a parse failure later. */
    private static final String TIME_PATTERN = "^([01]\\d|2[0-3]):[0-5]\\d$";

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

    /**
     * Cross-field: mirrors {@code FlightSearchHandler}'s check on the chat path — a party larger than
     * {@link PassengerCount#MAX_SEATS} seated passengers does not fit one booking.
     */
    @AssertTrue(message = "A search can include at most " + PassengerCount.MAX_SEATS + " seated passengers")
    private boolean isWithinSeatLimit() {
        if (adults == null) {
            return true;
        }
        return !toPassengerCount().exceedsSeatLimit();
    }

    /**
     * Cross-field: mirrors {@code FlightSearchHandler}'s check on the chat path — each infant flies on
     * an adult's lap, so a party can never hold more infants (age 0–2, per
     * {@link PassengerCount#ofChildAges}) than adults.
     */
    @AssertTrue(message = "Each infant needs an accompanying adult; infants cannot outnumber adults")
    private boolean isEachInfantAccompaniedByAnAdult() {
        if (adults == null) {
            return true;
        }
        return !toPassengerCount().hasMoreInfantsThanAdults();
    }

    /** Single source of truth for the typed party this request describes; null adults reads as none. */
    private PassengerCount toPassengerCount() {
        return PassengerCount.ofChildAges(adults != null ? adults : 0, childAges);
    }

    public FlightSearchCriteria toCriteria() {
        return FlightSearchCriteria.builder()
                .origin(origin)
                .destination(destination)
                .departDate(departDate)
                .returnDate(returnDate)
                .tripType(parseTripType(tripType))
                .passengers(toPassengerCount())
                .currency(currency)
                .nonstop(nonstop)
                .preferredAirline(airline)
                .departTimeFrom(departTimeRange == null ? null : parseTime(departTimeRange.from()))
                .departTimeTo(departTimeRange == null ? null : parseTime(departTimeRange.to()))
                .build();
    }

    /** Null/blank means "no bound". The @Pattern above has already rejected anything unparsable. */
    private static LocalTime parseTime(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalTime.parse(value.trim());
        } catch (DateTimeParseException e) {
            return null;
        }
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
