package com.paximum.paxassist.reservation.web.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

import com.paximum.paxassist.reservation.recovery.OrphanedBooking;

/**
 * Admin-facing view of an {@link OrphanedBooking} — a TourVisio purchase that committed but could not
 * be persisted locally. Not part of the customer-facing reservation contract; consumed by support
 * tooling only ({@code GET /api/v1/reservations/orphaned}, ADMIN-only).
 */
public record OrphanedBookingResponse(
        Long id,
        String externalReservationNumber,
        String intendedReservationNumber,
        String transactionId,
        Long userId,
        String leadGuestName,
        BigDecimal totalAmount,
        String currency,
        String failureReason,
        boolean reconciled,
        String resolutionNote,
        OffsetDateTime createdAt) {

    public static OrphanedBookingResponse from(OrphanedBooking booking) {
        return new OrphanedBookingResponse(
                booking.getId(),
                booking.getExternalReservationNumber(),
                booking.getIntendedReservationNumber(),
                booking.getTransactionId(),
                booking.getUserId(),
                booking.getLeadGuestName(),
                booking.getTotalAmount(),
                booking.getCurrency(),
                booking.getFailureReason(),
                booking.isReconciled(),
                booking.getResolutionNote(),
                booking.getCreatedAt());
    }
}
