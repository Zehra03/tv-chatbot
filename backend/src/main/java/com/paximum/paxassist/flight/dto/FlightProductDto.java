package com.paximum.paxassist.flight.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record FlightProductDto(
        String id,
        String airline,
        String flightNumber,
        String origin,
        String destination,
        Instant departTime,
        Instant arriveTime,
        Instant returnDepartTime,
        Instant returnArriveTime,
        int stops,
        int durationMinutes,
        String baggage,
        BigDecimal price,
        String currency) {
}