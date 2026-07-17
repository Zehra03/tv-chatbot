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
     * The provider's id for the outbound leg. Every round-trip option that flies out on this leg
     * shares it, which is what lets the return alternatives for a chosen outbound be found.
     */
    private final String outboundLegId;
    /** The provider's id for the return leg; null for a one-way. */
    private final String returnLegId;
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
    /** Human-readable summary of the fare's baggage, e.g. "1x20kg" — for display only. */
    private final String baggage;
    /**
     * The same fare's baggage in the form a filter can act on. Comes from the offer the
     * {@link #price} comes from, so the two always describe one buyable fare.
     */
    private final BaggageAllowance baggageAllowance;
    /** Total for the whole trip in the searched party's size — both legs for a round trip. */
    private final BigDecimal price;
    private final String currency;
}