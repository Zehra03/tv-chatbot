package com.paximum.paxassist.flight.domain;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder(toBuilder = true)
public class FlightSearchCriteria {
    private final String origin;
    private final String destination;
    private final LocalDate departDate;
    private final LocalDate returnDate;
    private final TripType tripType;
    private final PassengerCount passengers;
    private final String currency;
    private final Boolean nonstop;
    private final String preferredAirline;
    /**
     * Optional departure-time window (inclusive), local to the departure timestamps of whichever
     * search service produced the results — TourVisio's configured {@code tourvisio.timezone} for the
     * real path. Either bound may stand alone: only {@code departTimeFrom} means "at or after".
     */
    private final LocalTime departTimeFrom;
    private final LocalTime departTimeTo;

    public String toCacheKey() {
        return String.join("|",
                String.valueOf(origin),
                String.valueOf(destination),
                String.valueOf(departDate),
                String.valueOf(returnDate),
                String.valueOf(tripType),
                String.valueOf(currency),
                String.valueOf(nonstop),
                String.valueOf(preferredAirline),
                String.valueOf(departTimeFrom),
                String.valueOf(departTimeTo),
                passengers == null
                        ? "0A0C0I"
                        : passengers.getAdults() + "A" + passengers.getChildren() + "C" + passengers.getInfants() + "I");
    }

    /**
     * Fields a search cannot run without. A same-city route is reported here as a missing
     * {@code destination}: the value is present but unusable, and the destination is the field the
     * user has to correct — reporting it this way makes the chat re-ask "Nereye gitmek istersiniz?"
     * from the existing clarification catalogue instead of searching a route that yields nothing.
     * (The REST boundary rejects it earlier with a 400 — see
     * {@code FlightSearchApiRequest#isOriginDifferentFromDestination}; the chat path bypasses that
     * DTO entirely, which is why the rule also has to live on the criteria itself.)
     */
    public List<String> missingRequiredFields() {
        List<String> missing = new ArrayList<>();
        if (origin == null || origin.isBlank()) {
            missing.add("origin");
        }
        if (destination == null || destination.isBlank() || isSameAsOrigin()) {
            missing.add("destination");
        }
        if (departDate == null) {
            missing.add("departDate");
        }
        if (tripType == null) {
            missing.add("tripType");
        }
        if (tripType == TripType.ROUND_TRIP && returnDate == null) {
            missing.add("returnDate");
        }
        if (passengers == null || passengers.getAdults() < 1) {
            missing.add("passengers");
        }
        if (currency == null || currency.isBlank()) {
            missing.add("currency");
        }
        return missing;
    }

    /**
     * Case- and whitespace-insensitive: the chat path fills origin/destination from free text, so
     * "istanbul", "İstanbul " and "ISTANBUL" are one city here.
     */
    private boolean isSameAsOrigin() {
        if (origin == null || origin.isBlank()) {
            return false;
        }
        return canonicalCity(origin).equals(canonicalCity(destination));
    }

    /**
     * Folds the four Turkish i-variants (I ı İ i) onto one letter before lower-casing, because
     * neither locale gets both spellings right on its own: the Turkish locale turns "IZMIR" into
     * "ızmır" (dotless) so it stops matching "izmir", while the root locale turns "İSTANBUL" into
     * "i̇stanbul" (i + combining dot) so it stops matching "istanbul". Collapsing them first makes
     * the comparison agree with what a user means; distinct cities never differ by i-variant alone.
     */
    private static String canonicalCity(String value) {
        return value.trim()
                .replace('İ', 'i')
                .replace('I', 'i')
                .replace('ı', 'i')
                .toLowerCase(Locale.ROOT);
    }
}
