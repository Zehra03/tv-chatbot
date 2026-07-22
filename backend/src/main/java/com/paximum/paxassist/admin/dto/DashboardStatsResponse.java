package com.paximum.paxassist.admin.dto;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Admin dashboard summary counters.
 *
 * <p>{@code reservationsByProductType} is keyed by the lowercase product type
 * ("hotel" / "flight" / "combined") and counts BOOKINGS, not inventory: this system holds no
 * flight or hotel catalogue of its own — searchable products come live from TourVisio — so the
 * only flight number we can honestly report is how many flight reservations were made.
 */
public record DashboardStatsResponse(
        long totalReservations,
        long activeUsers,
        Map<String, BigDecimal> totalRevenueByCurrency,
        Map<String, Long> reservationsByProductType
) {}
