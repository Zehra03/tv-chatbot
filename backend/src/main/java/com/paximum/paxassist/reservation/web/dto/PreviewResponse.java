package com.paximum.paxassist.reservation.web.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

import com.paximum.paxassist.reservation.domain.ProductType;
import com.paximum.paxassist.reservation.domain.TripType;

/**
 * Response of POST /api/v1/reservations/preview — the summary screen's whole contract: what the user is
 * buying ({@code hotel} / {@code flight}: product, dates, party size), what it costs
 * ({@code totalAmount} + {@code currency}), that it is bookable ({@code available}), and the
 * {@code previewId} to confirm with.
 *
 * <p>{@code totalAmount} is TourVisio's live price, re-read while building the preview. When it differs
 * from what the search showed, {@code priceChanged} is true and {@code previousAmount} carries the old
 * figure, so the UI can show old vs new and take an explicit acceptance before confirming (K21).
 * {@code previousAmount} is null whenever the price did not move. {@code previousCurrency} is the
 * currency that old figure was actually declared in, which can differ from the live {@code currency} —
 * the UI must format {@code previousAmount} with {@code previousCurrency}, never with {@code currency}.
 *
 * <p>{@code available} is always true: a preview only exists for an offer TourVisio just agreed to
 * price. It is on the contract so the screen can state availability outright instead of implying it.
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
        BigDecimal previousAmount,
        String previousCurrency,
        boolean available,
        Hotel hotel,
        Flight flight) {

    /** The booked stay: where, when, how many nights, for how many people. */
    public record Hotel(
            String hotelName,
            String region,
            Short stars,
            String boardType,
            LocalDate checkIn,
            LocalDate checkOut,
            int nights,
            Short rooms,
            Short adults,
            Short children) {
    }

    /** The booked itinerary: route, trip type, departure/return, for how many people. */
    public record Flight(
            String origin,
            String destination,
            String airline,
            TripType tripType,
            OffsetDateTime departTime,
            OffsetDateTime returnDepartTime,
            Short passengerCount) {
    }
}
