package com.paximum.paxassist.reservation.web.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import com.paximum.paxassist.reservation.domain.ProductType;

/** Response of POST /api/v1/reservations/preview — the frozen summary + the handle to confirm with. */
public record PreviewResponse(
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
