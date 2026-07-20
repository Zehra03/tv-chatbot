package com.paximum.paxassist.orchestrator.mapper;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;

import org.springframework.stereotype.Component;

import com.paximum.paxassist.ai.SlotCriteria;
import com.paximum.paxassist.flight.domain.FlightSearchCriteria;
import com.paximum.paxassist.flight.domain.PassengerCount;
import com.paximum.paxassist.flight.domain.TripType;

/**
 * Adapter translating the AI layer's {@link SlotCriteria} into the flight module's
 * {@link FlightSearchCriteria}. Two inferences happen here:
 * <ul>
 *   <li><b>tripType</b>: a present return date implies {@link TripType#ROUND_TRIP}, else
 *       {@link TripType#ONE_WAY}. (Phase-1 simplification: we can't detect a round-trip
 *       intent when the user hasn't given a return date — see the note below.)</li>
 *   <li><b>passengers</b>: built only when an adult count exists; otherwise left null so the
 *       flight module reports "passengers" as a missing required field (single source of truth).
 *       Each accompanying child is typed by its age — see {@link #toPassengerCount}.</li>
 * </ul>
 *
 * <p>Not yet mapped: {@code cabinClass} — the flight criteria has no cabin field.
 */
@Component
public class FlightCriteriaMapper {

    private final GeoCountryResolver geoCountry;

    public FlightCriteriaMapper(GeoCountryResolver geoCountry) {
        this.geoCountry = geoCountry;
    }

    public FlightSearchCriteria toCriteria(SlotCriteria c) {
        LocalDate departDate = parse(c.departureDate());
        LocalDate returnDate = parse(c.returnDate());
        TripType tripType = (returnDate != null) ? TripType.ROUND_TRIP : TripType.ONE_WAY;

        // Each accompanying child is typed by its age (infant / child / adult fare) — the flight
        // domain owns that rule, since it is the provider's, not this adapter's.
        PassengerCount passengers =
                (c.adults() == null) ? null : PassengerCount.ofChildAges(c.adults(), c.childAges());

        // The user is never asked for a currency; it follows from where the request came from
        // unless they explicitly named one.
        String currency = CurrencyByCountry.resolve(c.currency(), geoCountry.currentCountry().orElse(null));

        return FlightSearchCriteria.builder()
                .origin(c.origin())
                .destination(c.destination())
                .departDate(departDate)
                .returnDate(returnDate)
                .tripType(tripType)
                .passengers(passengers)
                .currency(currency)
                .nonstop(null)
                .preferredAirline(null)
                // Baggage reaches the SEARCH rather than the card filters: it decides which fare of a
                // flight is priced, so it cannot be applied to finished cards (see
                // TourVisioFlightResponseMapper#cheapestOffer).
                .checkedBaggage(c.checkedBaggage())
                .minCheckedBaggageKg(c.minCheckedBaggageKg())
                .build();
    }

    private LocalDate parse(String date) {
        if (date == null) {
            return null;
        }
        try {
            return LocalDate.parse(date);
        } catch (DateTimeParseException e) {
            return null;
        }
    }
}
