package com.paximum.paxassist.flight.domain;

import java.math.BigDecimal;
import java.time.Instant;

import lombok.Builder;
import lombok.Getter;
import lombok.extern.jackson.Jacksonized;

@Getter
@Builder
@Jacksonized
public class FlightProduct {
    private final String id;
    /** Offer token from TourVisio, required when handing the flight off to the reservation flow. */
    private final String offerId;
    private final String airline;
    private final String flightNumber;
    private final String origin;
    private final String destination;
    /** City the origin/destination airport belongs to (e.g. code SAW → "Istanbul"), from TourVisio. */
    private final String originCity;
    private final String destinationCity;
    private final Instant departTime;
    private final Instant arriveTime;
    private final Instant returnDepartTime;
    private final Instant returnArriveTime;
    private final int stops;
    private final int durationMinutes;
    private final String baggage;
    private final BigDecimal price;
    private final String currency;
}