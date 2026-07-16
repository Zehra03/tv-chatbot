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
    /**
     * Offer token for the OUTBOUND leg, required when handing the flight off to the reservation
     * flow. A round trip is booked with this plus {@link #returnOfferId} — TourVisio prices and
     * tokens each leg separately, so one id alone would book a one-way.
     */
    private final String offerId;
    /** Offer token for the return leg; null for a one-way. */
    private final String returnOfferId;
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
    /** Return leg details; null/zero for a one-way. */
    private final String returnAirline;
    private final int returnStops;
    private final int stops;
    private final int durationMinutes;
    private final String baggage;
    /** Total for the whole trip in the searched party's size — both legs for a round trip. */
    private final BigDecimal price;
    private final String currency;
}