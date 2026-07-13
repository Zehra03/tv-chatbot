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
                    // Invalid checkOut date, reset to 1 night
                    nights = 1;
                    checkOut = checkIn.plusDays(1);
                    checkOutStr = checkOut.toString();
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
            returnDate = departureDate.plusDays(1);
            returnDateStr = returnDate.toString();
        }

        return new SlotCriteria(
                criteria.location(),
                checkInStr,
                checkOutStr,
                nights,
                criteria.rooms(),
                criteria.stars(),
                criteria.boardType(),
                criteria.features(),
                criteria.hotelMaxPrice(),
                criteria.origin(),
                criteria.destination(),
                departureDateStr,
                returnDateStr,
                criteria.cabinClass(),
                criteria.flightMaxPrice(),
                criteria.adults(),
                criteria.children(),
                criteria.childAges(),
                criteria.nationality(),
                criteria.currency(),
                criteria.sortBy(),
                criteria.selectionReference()
        );
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
