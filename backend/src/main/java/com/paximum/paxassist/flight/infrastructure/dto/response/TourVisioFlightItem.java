package com.paximum.paxassist.flight.infrastructure.dto.response;

public record TourVisioFlightItem(
        String flightNo,
        int duration,
        TourVisioFlightPoint departure,
        TourVisioFlightPoint arrival) {
}