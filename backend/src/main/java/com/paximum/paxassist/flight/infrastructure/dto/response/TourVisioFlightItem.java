package com.paximum.paxassist.flight.infrastructure.dto.response;

import java.util.List;

public record TourVisioFlightItem(
        String flightNo,
        int duration,
        int stopCount,
        TourVisioAirline airline,
        TourVisioFlightPoint departure,
        TourVisioFlightPoint arrival,
        List<TourVisioBaggageInfo> baggageInformations) {
}