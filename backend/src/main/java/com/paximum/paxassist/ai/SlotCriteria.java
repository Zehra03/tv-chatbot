

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
 *                directFlight, airline, departTimeRange, checkedBaggage, minCheckedBaggageKg,
 *                tripType
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
        /*
         * one_way | round_trip — the trip the user ASKED for, which is not the same thing as whether
         * a return date is already known. Without it "gidiş-dönüş uçuş arıyorum" with no date yet
         * looked identical to a one-way search, so the search ran one-way instead of asking for the
         * return date (FlightSearchCriteria#missingRequiredFields reports "returnDate" only when the
         * trip type says ROUND_TRIP). Values match the frontend's TripType union.
         */
        String tripType,

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
                // flight (12): origin, destination, departureDate, returnDate, cabinClass,
                //              flightMaxPrice, directFlight, airline, departTimeRange,
                //              checkedBaggage, minCheckedBaggageKg, tripType
                null, null, null, null, null, null, null, null, null, null, null, null,
                // shared (5) + filter/sort (2) + select (1)
                null, null, null, null, null, null, null, null);
    }

    /**
     * Copy carrying the given trip type. The chat handler uses it to write back what a deterministic
     * reading of the user's own words found ("gidiş-dönüş"), so the value accumulates like any other
     * slot instead of being recomputed from scratch every turn.
     */
    public SlotCriteria withTripType(String tripType) {
        return new SlotCriteria(
                location, checkIn, checkOut, nights, rooms, stars, maxStars, boardType, features,
                hotelMaxPrice,
                origin, destination, departureDate, returnDate, cabinClass, flightMaxPrice,
                directFlight, airline, departTimeRange, checkedBaggage, minCheckedBaggageKg, tripType,
                adults, children, childAges, nationality, currency,
                sortBy, limit, selectionReference);
    }
}
