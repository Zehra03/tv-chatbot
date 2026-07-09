package com.paximum.paxassist.reservation.web.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.paximum.paxassist.reservation.domain.ProductType;
import com.paximum.paxassist.reservation.domain.ReservationStatus;

/** List-row / created-reservation view — header fields only, no passengers or product details. */
public record ReservationSummaryResponse(
        Long id,
        String reservationNumber,
        String externalReservationNumber,
        ReservationStatus status,
        ProductType productType,
        LocalDate reservationDate,
        BigDecimal totalAmount,
        String currency,
        String leadGuestName) {
}
