package com.paximum.paxassist.ai;

import java.util.List;

/**
 * Partially-filled criteria extracted from a single user message.
 * All fields are nullable — only what the user explicitly stated is populated.
 * The orchestrator merges multiple turns into accumulated criteria before
 * routing to hotel.HotelSearchCriteria or flight.FlightSearchCriteria.
 *
 * Hotel fields : location, checkIn, checkOut, nights, adults, children, childAges,
 *                nationality, currency, rooms, stars, boardType, sortBy
 * Flight fields: origin, destination, departureDate, returnDate, cabinClass
 * Shared        : adults, children, childAges, nationality, currency, maxPrice
 * SELECT intent : selectionReference
 */
public record SlotCriteria(

        // ── Hotel ────────────────────────────────────────────────────────────
        String location,
        String checkIn,           // YYYY-MM-DD
        String checkOut,          // YYYY-MM-DD
        Integer nights,           // number of nights when the user gives a count instead of a checkout date
        Integer rooms,
        Integer stars,            // minimum star rating
        String boardType,         // AI | HB | BB | RO

        // ── Flight ───────────────────────────────────────────────────────────
        String origin,            // departure city or airport
        String destination,       // arrival city or airport
        String departureDate,     // YYYY-MM-DD
        String returnDate,        // YYYY-MM-DD — null means one-way
        String cabinClass,        // ECONOMY | BUSINESS | FIRST

        // ── Shared (hotel + flight) ───────────────────────────────────────────
        Integer adults,
        Integer children,
        List<Integer> childAges,
        String nationality,       // ISO-3166 alpha-2
        String currency,          // ISO-4217
        Integer maxPrice,         // user-stated upper price limit, e.g. "1800 tl max" → 1800

        // ── Filter / sort (FILTER intent) ────────────────────────────────────
        String sortBy,            // price_asc | price_desc | stars_desc

        // ── Selection (SELECT intent) ─────────────────────────────────────────
        String selectionReference // raw user text: "1", "ilk", "en ucuz olan"
) {
}
