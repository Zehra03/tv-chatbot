package com.paximum.paxassist.reservation.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import com.paximum.paxassist.reservation.domain.ProductType;

/**
 * Summary returned by {@code previewReservation} and shown to the user before the final confirm.
 * Carries the {@link #previewId()} the caller must pass to {@code confirmReservation}, plus the frozen
 * price/passengers/product summary and the {@link #expiresAt()} of the Redis snapshot.
 *
 * <p>No TourVisio call produced this — it is assembled purely from validated input.
 */
public record ReservationPreview(
        String previewId,
        Instant expiresAt,
        ProductType productType,
        BigDecimal totalAmount,
        String currency,
        String leadGuestName,
        List<String> passengerNames,
        boolean hasHotel,
        boolean hasFlight) {
}
