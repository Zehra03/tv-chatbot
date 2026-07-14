package com.paximum.paxassist.flight.infrastructure.dto.response;

import java.util.List;

/**
 * One flight segment of a TourVisio price-search result. {@code route} tells which leg the segment
 * belongs to: {@code 1} = outbound, {@code 2} = inbound/return. A leg with a layover arrives as
 * several segments sharing the same {@code route}. One-way payloads may omit it, so it is nullable
 * and absence is read as outbound.
 */
public record TourVisioFlightItem(
        String flightNo,
        Integer route,
        int duration,
        int stopCount,
        TourVisioAirline airline,
        TourVisioFlightPoint departure,
        TourVisioFlightPoint arrival,
        List<TourVisioBaggageInfo> baggageInformations) {
}
