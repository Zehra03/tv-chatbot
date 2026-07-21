package com.paximum.paxassist.orchestrator.slot;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;

import org.springframework.stereotype.Component;

import com.paximum.paxassist.ai.SlotCriteria;

/**
 * Ensures that all dates within the SlotCriteria are logically sound.
 * - Prevents past dates (shifts them to tomorrow).
 * - Resolves conflicts between checkIn, checkOut, and nights.
 * - Ensures returnDate is not before departureDate.
 */
@Component
public class SlotNormalizer {

    /** Wire values of the trip type, matching the frontend's {@code TripType} union. */
    public static final String TRIP_TYPE_ROUND = "round_trip";
    public static final String TRIP_TYPE_ONE_WAY = "one_way";

    public SlotCriteria normalize(SlotCriteria criteria) {
        if (criteria == null) {
            return null;
        }

        LocalDate today = LocalDate.now();

        // 1. Hotel Logic
        String checkInStr = criteria.checkIn();
        LocalDate checkIn = parseOrNull(checkInStr);
        if (checkIn != null && checkIn.isBefore(today)) {
            checkIn = null;
            checkInStr = null;
        }

        String checkOutStr = criteria.checkOut();
        LocalDate checkOut = parseOrNull(checkOutStr);
        if (checkOut != null && checkOut.isBefore(today)) {
            checkOut = null;
            checkOutStr = null;
        }

        Integer nights = criteria.nights();

        if (checkIn != null) {
            if (nights != null && nights > 0) {
                // Nights take precedence if both are provided or checkout is missing
                checkOut = checkIn.plusDays(nights);
                checkOutStr = checkOut.toString();
            } else if (checkOut != null) {
                if (!checkOut.isAfter(checkIn)) {
                    // Invalid checkOut date, clear it so it can be handled by guards or clarification
                    checkOut = null;
                    checkOutStr = null;
                } else {
                    // Valid checkOut, compute nights
                    nights = (int) ChronoUnit.DAYS.between(checkIn, checkOut);
                }
            }
        }

        // 2. Flight Logic
        String departureDateStr = criteria.departureDate();
        LocalDate departureDate = parseOrNull(departureDateStr);
        if (departureDate != null && departureDate.isBefore(today)) {
            departureDate = null;
            departureDateStr = null;
        }

        String returnDateStr = criteria.returnDate();
        LocalDate returnDate = parseOrNull(returnDateStr);
        if (returnDate != null && returnDate.isBefore(today)) {
            returnDate = null;
            returnDateStr = null;
        }

        if (returnDate != null && departureDate != null && returnDate.isBefore(departureDate)) {
            // Return date cannot be before departure date
            returnDate = null;
            returnDateStr = null;
        }

        // A known return date IS a round trip, whether or not the user said the words. The reverse
        // does not hold: "gidiş-dönüş" with no date yet stays ROUND_TRIP so the handler asks for the
        // return date instead of quietly searching one-way.
        String tripType = normalizeTripType(criteria.tripType());
        if (returnDateStr != null) {
            tripType = TRIP_TYPE_ROUND;
        }

        // 3. Numeric Limits
        Integer adults = criteria.adults() != null && criteria.adults() > 0 ? criteria.adults() : null;
        Integer children = criteria.children() != null && criteria.children() >= 0 ? criteria.children() : null;
        
        java.util.List<Integer> childAges = criteria.childAges();
        if (childAges != null) {
            // Keep only valid child ages (0-17). SlotGuard already asks the user about out-of-range
            // ages before search; this is the defensive net so an invalid age can never be persisted.
            childAges = childAges.stream().filter(age -> age >= 0 && age <= 17).toList();
            if (childAges.isEmpty()) {
                childAges = null;
            }
        }

        Integer rooms = criteria.rooms() != null && criteria.rooms() > 0 ? criteria.rooms() : null;
        Integer hotelMaxPrice = criteria.hotelMaxPrice() != null && criteria.hotelMaxPrice() > 0 ? criteria.hotelMaxPrice() : null;
        Integer flightMaxPrice = criteria.flightMaxPrice() != null && criteria.flightMaxPrice() > 0 ? criteria.flightMaxPrice() : null;

        return new SlotCriteria(
                criteria.location(),
                checkInStr,
                checkOutStr,
                nights,
                rooms,
                criteria.stars(),
                criteria.maxStars(),
                criteria.boardType(),
                criteria.features(),
                hotelMaxPrice,
                criteria.origin(),
                criteria.destination(),
                departureDateStr,
                returnDateStr,
                criteria.cabinClass(),
                flightMaxPrice,
                criteria.directFlight(),
                criteria.airline(),
                criteria.departTimeRange(),
                criteria.checkedBaggage(),
                // A non-positive kg threshold is not a threshold; drop it rather than let it reach
                // the search, where every fare would trivially "meet" it.
                criteria.minCheckedBaggageKg() != null && criteria.minCheckedBaggageKg() > 0
                        ? criteria.minCheckedBaggageKg() : null,
                tripType,
                adults,
                children,
                childAges,
                criteria.nationality(),
                criteria.currency(),
                criteria.sortBy(),
                criteria.limit(),
                criteria.selectionReference()
        );
    }

    /**
     * Accepts what either producer may write — the detector's own values, or an LLM answering with
     * {@code ROUND_TRIP} / "roundtrip" / "gidiş-dönüş" — and folds them onto the two wire values.
     * Anything unrecognized becomes null: an unknown trip type must not make the search ask for a
     * return date it will never accept.
     */
    private String normalizeTripType(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String value = raw.trim().toLowerCase(java.util.Locale.ROOT).replace('-', '_').replace(' ', '_');
        if (value.equals("round_trip") || value.equals("roundtrip") || value.equals("return")
                || value.startsWith("gidiş_dönüş") || value.startsWith("gidis_donus")) {
            return TRIP_TYPE_ROUND;
        }
        if (value.equals("one_way") || value.equals("oneway") || value.startsWith("tek_yön")
                || value.startsWith("tek_yon")) {
            return TRIP_TYPE_ONE_WAY;
        }
        return null;
    }

    private LocalDate parseOrNull(String dateStr) {
        if (dateStr == null || dateStr.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(dateStr);
        } catch (DateTimeParseException e) {
            return null;
        }
    }
}
