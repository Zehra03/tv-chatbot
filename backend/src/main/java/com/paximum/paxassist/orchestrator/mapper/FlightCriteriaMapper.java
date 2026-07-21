package com.paximum.paxassist.orchestrator.mapper;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;

import org.springframework.stereotype.Component;

import com.paximum.paxassist.ai.SlotCriteria;
import com.paximum.paxassist.flight.domain.FlightSearchCriteria;
import com.paximum.paxassist.flight.domain.PassengerCount;
import com.paximum.paxassist.flight.domain.TripType;
import com.paximum.paxassist.orchestrator.slot.SlotNormalizer;

/**
 * Adapter translating the AI layer's {@link SlotCriteria} into the flight module's
 * {@link FlightSearchCriteria}. Two inferences happen here:
 * <ul>
 *   <li><b>tripType</b>: the trip type the user stated ({@code SlotCriteria#tripType}, filled by
 *       {@code TripTypeDetector} / extraction) if any; otherwise inferred from the dates — a present
 *       return date means {@link TripType#ROUND_TRIP}, else {@link TripType#ONE_WAY}.</li>
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
        TripType tripType = tripType(c.tripType(), returnDate);

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

    /**
     * The trip the user asked for wins over what the dates happen to show. A stated round trip with
     * no return date yet therefore stays {@link TripType#ROUND_TRIP}, which is exactly what makes the
     * flight module report {@code returnDate} as missing so the chat asks for it — before this, the
     * absent date silently turned the request into a one-way search.
     *
     * <p>A return date still implies a round trip when the user never named a direction, and an
     * explicit "tek yön" wins over a leftover date (the handler also clears it).
     */
    private TripType tripType(String statedTripType, LocalDate returnDate) {
        if (SlotNormalizer.TRIP_TYPE_ROUND.equals(statedTripType)) {
            return TripType.ROUND_TRIP;
        }
        if (SlotNormalizer.TRIP_TYPE_ONE_WAY.equals(statedTripType)) {
            return TripType.ONE_WAY;
        }
        return (returnDate != null) ? TripType.ROUND_TRIP : TripType.ONE_WAY;
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
