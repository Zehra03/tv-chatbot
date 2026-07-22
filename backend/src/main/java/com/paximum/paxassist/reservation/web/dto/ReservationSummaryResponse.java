package com.paximum.paxassist.reservation.web.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.paximum.paxassist.reservation.domain.ProductType;
import com.paximum.paxassist.reservation.domain.ReservationStatus;

/**
 * List-row / created-reservation view — header fields only, no passengers or product details.
 *
 * <p>{@code guest} says the booking was made without an account. It only tells the admin list
 * apart member from guest rows; it is derived (see {@code Reservation.isGuest()}) rather than
 * stored, and it deliberately does NOT expose the owning user id or guest token.
 */
public record ReservationSummaryResponse(
        Long id,
        String reservationNumber,
        String externalReservationNumber,
        ReservationStatus status,
        ProductType productType,
        LocalDate reservationDate,
        BigDecimal totalAmount,
        String currency,
        String leadGuestName,
        boolean guest) {
}
