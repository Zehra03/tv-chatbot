

package com.paximum.paxassist.ai;

import java.util.List;

/**
 * Partially-filled criteria extracted from a single user message.
 * All fields are nullable — only what the user explicitly stated is populated.
 * The orchestrator merges multiple turns into accumulated criteria before
 * routing to hotel.HotelSearchCriteria or flight.FlightSearchCriteria.
 *
 * Hotel fields : location, checkIn, checkOut, nights, adults, children, childAges,
 *                nationality, currency, rooms, stars, boardType, features, hotelMaxPrice, sortBy
 * Flight fields: origin, destination, departureDate, returnDate, cabinClass, flightMaxPrice,
 *                directFlight, airline, departTimeRange, checkedBaggage, minCheckedBaggageKg
 * Shared        : adults, children, childAges, nationality, currency
 * SELECT intent : selectionReference
 *
 * Budgets are per-domain (hotelMaxPrice vs flightMaxPrice), not shared: the same conversation
 * can carry a hotel budget and a separate flight budget without one overriding the other.
 */

public record SlotCriteria(

        // ── Hotel ────────────────────────────────────────────────────────────
        String location,
        String checkIn,           // YYYY-MM-DD
        String checkOut,          // YYYY-MM-DD
        Integer nights,           // number of nights when the user gives a count instead of a checkout date
        Integer rooms,
        Integer stars,            // minimum star rating
        Integer maxStars,         // maximum star rating
        String boardType,         // AI | HB | BB | RO
        List<String> features,    // requested hotel features, e.g. ["SEAFRONT","POOL"] — see orchestrator.intent.HotelFeature
        Integer hotelMaxPrice,    // upper price limit for a HOTEL search, e.g. "otelde 18000 tl max" → 18000

        // ── Flight ───────────────────────────────────────────────────────────
        String origin,            // departure city or airport
        String destination,       // arrival city or airport
        String departureDate,     // YYYY-MM-DD
        String returnDate,        // YYYY-MM-DD — null means one-way
        String cabinClass,        // ECONOMY | BUSINESS | FIRST
        Integer flightMaxPrice,   // upper price limit for a FLIGHT search, e.g. "uçuşa 3000 tl max" → 3000
        Boolean directFlight,     // true: direct/non-stop, false: layovers, null: any
        String airline,           // preferred airline as the user stated it, e.g. "THY", "Pegasus"
        String departTimeRange,   // departure time-of-day bucket: morning | afternoon | evening | night
        Boolean checkedBaggage,   // true: fare must include checked baggage, false: cabin-only fare, null: any
        Integer minCheckedBaggageKg, // minimum checked allowance in kg, "15 kilo bagajlı" → 15

        // ── Shared (hotel + flight) ───────────────────────────────────────────
        Integer adults,
        Integer children,
        List<Integer> childAges,
        String nationality,       // ISO-3166 alpha-2
        String currency,          // ISO-4217

        // ── Filter / sort (FILTER intent) ────────────────────────────────────
        String sortBy,            // price_asc | price_desc | stars_desc
        Integer limit,            // number of results to display

        // ── Selection (SELECT intent) ─────────────────────────────────────────
        String selectionReference // raw user text: "1", "ilk", "en ucuz olan"
) {

    /**
     * An all-null criteria — used so slot accumulation never yields {@code null} when a turn
     * carries an intent but no slots yet (e.g. "otel arıyorum"), keeping the mappers null-safe.
     */
    public static SlotCriteria empty() {
        return new SlotCriteria(
                // hotel (10)
                null, null, null, null, null, null, null, null, null, null,
                // flight (11): origin, destination, departureDate, returnDate, cabinClass,
                //              flightMaxPrice, directFlight, airline, departTimeRange,
                //              checkedBaggage, minCheckedBaggageKg
                null, null, null, null, null, null, null, null, null, null, null,
                // shared (5) + filter/sort (2) + select (1)
                null, null, null, null, null, null, null, null);
    }
}
