package com.paximum.paxassist.ai;

import java.util.List;

/**
 * Partially-filled search criteria extracted by the AI from user messages.
 * All fields are nullable — only what the user has stated so far is populated.
 * Completed criteria are handed off to hotel.HotelSearchCriteria for TourVisio.
 */
public record SlotCriteria(
        String location,
        String checkIn,        // YYYY-MM-DD
        String checkOut,       // YYYY-MM-DD
        Integer adults,
        Integer children,
        List<Integer> childAges,
        String nationality,
        String currency,
        Integer rooms,
        Integer stars,         // optional filter
        String boardType,      // optional filter: AI, HB, BB...
        String sortBy          // optional: price_asc, stars_desc...
) {
}
