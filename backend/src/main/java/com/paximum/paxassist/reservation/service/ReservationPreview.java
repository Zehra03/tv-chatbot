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
 * <p>{@link #totalAmount()} is TourVisio's own live price, re-read while building this preview — never
 * the client's declared figure. When the two differ, {@link #priceChanged()} is true and
 * {@link #previousAmount()} carries what the user was originally shown, so the UI can present old vs
 * new and take an explicit acceptance before confirming (K21).
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
        boolean hasFlight,
        boolean priceChanged,
        BigDecimal previousAmount) {
}
