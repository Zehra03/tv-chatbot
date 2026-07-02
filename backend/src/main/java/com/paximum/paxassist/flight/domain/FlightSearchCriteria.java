package com.paximum.paxassist.flight.domain;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
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
                passengers == null
                        ? "0A0C0I"
                        : passengers.getAdults() + "A" + passengers.getChildren() + "C" + passengers.getInfants() + "I");
    }

    public List<String> missingRequiredFields() {
        List<String> missing = new ArrayList<>();
        if (origin == null || origin.isBlank()) {
            missing.add("origin");
        }
        if (destination == null || destination.isBlank()) {
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
}
