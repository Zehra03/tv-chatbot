package com.paximum.paxassist.admin.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.paximum.paxassist.reservation.domain.ProductType;
import com.paximum.paxassist.reservation.domain.ReservationStatus;

/**
 * Admin list row: the reservation header plus WHO it belongs to.
 *
 * <p>Separate from {@code ReservationSummaryResponse} on purpose. That DTO also answers a user's own
 * list and the 201 body of a booking a guest just made; putting owner identity on it would add a
 * field that is meaningful on exactly one of those endpoints and null on the rest.
 *
 * <p>{@code ownerEmail} / {@code ownerName} are null for guest bookings — those have no account by
 * definition, and {@code guest} says so. The opaque guest token is deliberately NOT exposed: it is a
 * bearer key that grants access to the booking, so it belongs in no response body.
 */
public record AdminReservationResponse(
        Long id,
        String reservationNumber,
        String externalReservationNumber,
        ReservationStatus status,
        ProductType productType,
        LocalDate reservationDate,
        BigDecimal totalAmount,
        String currency,
        String leadGuestName,
        boolean guest,
        String ownerEmail,
        String ownerName) {
}
