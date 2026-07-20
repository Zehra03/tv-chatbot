package com.paximum.paxassist.hotel.dto;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * The request body {@code POST /api/v1/hotels/search} receives from the frontend
 * ({@code HotelSearchCriteria} in {@code frontend/src/types/search.ts}). Only the fields the
 * search needs are mapped to the internal {@link HotelSearchRequest}; frontend-only filters
 * (stars, boardType, priceRange, region, sort, hotelName, rooms, children) are applied client-side
 * and ignored here.
 *
 * <p>Constraints are enforced here rather than left to the frontend: this endpoint is reachable
 * directly, and an unchecked body reaches TourVisio as a nonsensical search (zero adults, a
 * negative night count from an inverted date range, a check-in in the past) that only fails after
 * a provider round-trip — the user waits for an error we could have given immediately.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record HotelSearchApiRequest(
        @NotBlank(message = "Varış yeri zorunludur.")
        String destination,

        @NotNull(message = "Giriş tarihi zorunludur.")
        @FutureOrPresent(message = "Geçmiş tarih seçilemez.")
        @com.paximum.paxassist.validator.MaxSearchDate
        LocalDate checkIn,

        @NotNull(message = "Çıkış tarihi zorunludur.")
        LocalDate checkOut,

        @NotNull(message = "Yetişkin sayısı zorunludur.")
        @Min(value = 1, message = "En az 1 yetişkin olmalıdır.")
        Integer adults,

        Integer children,

        List<Integer> childAges,
        String nationality,
        String currency) {

    /** Cross-field rule: an inverted or same-day range would map to a zero/negative night count. */
    @JsonIgnore
    @AssertTrue(message = "Çıkış tarihi giriş tarihinden sonra olmalıdır.")
    public boolean isCheckOutAfterCheckIn() {
        // Null dates are already reported by @NotNull; don't double-report them here.
        return checkIn == null || checkOut == null || checkOut.isAfter(checkIn);
    }

    /** Maps to the internal request: nights are derived from check-in/check-out, adults→adult. */
    public HotelSearchRequest toInternal() {
        Integer night = (checkIn != null && checkOut != null)
                ? (int) ChronoUnit.DAYS.between(checkIn, checkOut)
                : null;
        String checkInIso = (checkIn != null) ? checkIn.toString() : null;
        return new HotelSearchRequest(destination, checkInIso, night, adults, childAges, nationality, currency, null);
    }
}
