package com.paximum.paxassist.hotel.dto;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * The request body {@code POST /api/v1/hotels/search} receives from the frontend
 * ({@code HotelSearchCriteria} in {@code frontend/src/types/search.ts}). Only the fields the
 * search needs are mapped to the internal {@link HotelSearchRequest}; frontend-only filters
 * (stars, boardType, priceRange, region, sort, hotelName, rooms, children) are applied client-side
 * and ignored here.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record HotelSearchApiRequest(
        String destination,
        LocalDate checkIn,
        LocalDate checkOut,
        Integer adults,
        List<Integer> childAges,
        String nationality,
        String currency) {

    /** Maps to the internal request: nights are derived from check-in/check-out, adults→adult. */
    public HotelSearchRequest toInternal() {
        Integer night = (checkIn != null && checkOut != null)
                ? (int) ChronoUnit.DAYS.between(checkIn, checkOut)
                : null;
        String checkInIso = (checkIn != null) ? checkIn.toString() : null;
        return new HotelSearchRequest(destination, checkInIso, night, adults, childAges, nationality, currency, null);
    }
}
