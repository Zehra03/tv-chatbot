package com.paximum.paxassist.reservation.web.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import com.paximum.paxassist.reservation.domain.ProductType;

/**
 * Response of POST /api/v1/reservations/preview — the frozen summary + the handle to confirm with.
 *
 * <p>{@code totalAmount} is TourVisio's live price, re-read while building the preview. When it differs
 * from what the search showed, {@code priceChanged} is true and {@code previousAmount} carries the old
 * figure, so the UI can show old vs new and take an explicit acceptance before confirming (K21).
 * {@code previousAmount} is null whenever the price did not move.
 */
public record PreviewResponse(
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
