package com.paximum.paxassist.flight.dto;

import java.math.BigDecimal;
import java.time.Instant;

import com.paximum.paxassist.flight.domain.FlightProduct;
import com.paximum.paxassist.flight.domain.TripType;

/**
 * One flight card in the frontend's shape ({@code FlightProduct} in
 * {@code frontend/src/types/product.ts}): carries {@code tripType} ({@code one_way}/{@code round_trip})
 * and drops the internal {@code flightNumber}.
 */
public record FlightProductApiDto(
        String id,
        String airline,
        String origin,
        String destination,
        Instant departTime,
        Instant arriveTime,
        String tripType,
        Instant returnDepartTime,
        Instant returnArriveTime,
        int stops,
        String baggage,
        BigDecimal price,
        String currency) {

    public static FlightProductApiDto from(FlightProduct product, TripType tripType) {
        return new FlightProductApiDto(
                product.getId(),
                product.getAirline(),
                product.getOrigin(),
                product.getDestination(),
                product.getDepartTime(),
                product.getArriveTime(),
                tripType == TripType.ROUND_TRIP ? "round_trip" : "one_way",
                product.getReturnDepartTime(),
                product.getReturnArriveTime(),
                product.getStops(),
                product.getBaggage(),
                product.getPrice(),
                product.getCurrency());
    }
}
