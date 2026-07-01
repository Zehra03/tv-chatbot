package com.paximum.paxassist.flight.domain;

import java.time.LocalDate;

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
                passengers.getAdults() + "A" + passengers.getChildren() + "C" + passengers.getInfants() + "I");
    }
}
