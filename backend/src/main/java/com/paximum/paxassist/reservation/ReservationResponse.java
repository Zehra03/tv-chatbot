package com.paximum.paxassist.reservation;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public record ReservationResponse(
        Long id,
        String reservationNumber,
        String productType,
        String status,
        LocalDate reservationDate,
        BigDecimal totalAmount,
        String currency,
        String leadGuestName,
        Instant createdAt
) {}
