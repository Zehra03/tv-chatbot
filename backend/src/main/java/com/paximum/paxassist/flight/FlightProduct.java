package com.paximum.paxassist.flight;

import java.math.BigDecimal;
import java.time.Instant;

public record FlightProduct(
        String id,
        String airline,
        String origin,
        String destination,
        String tripType,
        Instant departTime,
        Instant arriveTime,
        Instant returnDepartTime,
        Instant returnArriveTime,
        int stops,
        String baggage,
        int passengerCount,
        BigDecimal price,
        String currency
) {}
