package com.paximum.paxassist.flight.service;

import java.util.List;

import com.paximum.paxassist.flight.domain.FlightProduct;

public record FlightSearchOutcome(boolean complete, List<String> missingFields, List<FlightProduct> results) {

    public static FlightSearchOutcome incomplete(List<String> missingFields) {
        return new FlightSearchOutcome(false, missingFields, List.of());
    }

    public static FlightSearchOutcome complete(List<FlightProduct> results) {
        return new FlightSearchOutcome(true, List.of(), results);
    }
}
