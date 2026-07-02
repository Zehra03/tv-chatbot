package com.paximum.paxassist.flight.event;

import java.time.Instant;

import com.paximum.paxassist.flight.domain.FlightSearchCriteria;

public record FlightSearchEvent(
        String origin,
        String destination,
        String tripType,
        boolean success,
        int resultCount,
        Instant occurredAt) {

    public static FlightSearchEvent success(FlightSearchCriteria criteria, int resultCount) {
        return new FlightSearchEvent(
                criteria.getOrigin(),
                criteria.getDestination(),
                String.valueOf(criteria.getTripType()),
                true,
                resultCount,
                Instant.now());
    }

    public static FlightSearchEvent failure(FlightSearchCriteria criteria) {
        return new FlightSearchEvent(
                criteria.getOrigin(),
                criteria.getDestination(),
                String.valueOf(criteria.getTripType()),
                false,
                0,
                Instant.now());
    }
}
