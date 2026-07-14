package com.paximum.paxassist.reservation.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

import com.paximum.paxassist.reservation.domain.ProductType;
import com.paximum.paxassist.reservation.domain.TripType;

/**
 * Summary returned by {@code previewReservation} and shown to the user before the final confirm.
 * Carries the {@link #previewId()} the caller must pass to {@code confirmReservation}, plus everything
 * the user needs to see what they are about to buy: the product, its dates, the party size, the price,
 * the currency, and the fact that it was just confirmed bookable.
 *
 * <p>{@link #totalAmount()} is TourVisio's own live price, re-read while building this preview — never
 * the client's declared figure. When the two differ, {@link #priceChanged()} is true and
 * {@link #previousAmount()} carries what the user was originally shown, so the UI can present old vs
 * new and take an explicit acceptance before confirming (K21).
 *
 * <p>{@link #available()} is always true here: a preview only exists when TourVisio agreed to price the
 * offer. A sold-out product never reaches this record — it comes back as {@link PreviewResult.Unavailable}.
 * The flag is on the contract so the summary screen can state availability explicitly rather than imply it.
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
        BigDecimal previousAmount,
        boolean available,
        Hotel hotel,
        Flight flight) {

    /** The booked stay as the user must see it: where, when, how many nights, and for how many people. */
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

    /** The booked itinerary: route, trip type, when it departs and comes back, and for how many people. */
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
