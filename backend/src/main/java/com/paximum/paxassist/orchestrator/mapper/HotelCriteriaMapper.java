package com.paximum.paxassist.orchestrator.mapper;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;

import org.springframework.stereotype.Component;

import com.paximum.paxassist.ai.SlotCriteria;
import com.paximum.paxassist.hotel.dto.HotelSearchRequest;

/**
 * Adapter (anti-corruption layer) translating the AI layer's {@link SlotCriteria} vocabulary
 * into the hotel module's {@link HotelSearchRequest}. Neither module knows about the other —
 * only this mapper bridges them.
 *
 * <p>Key translation: the AI captures {@code checkIn}/{@code checkOut} dates, but the hotel
 * module wants a {@code night} count, so we compute the day span here. If dates are missing
 * or unparseable we pass {@code null} night through and let the hotel module report it as a
 * missing parameter — completeness stays a single source of truth in the hotel module.
 *
 * <p>Not yet mapped (Phase 1): {@code rooms}, {@code stars}, {@code boardType} — the current
 * {@code HotelSearchRequest} has no fields for them; {@code stars}/{@code boardType} are applied
 * as post-search filters via the FILTER intent instead.
 */
@Component
public class HotelCriteriaMapper {

    public HotelSearchRequest toRequest(SlotCriteria c) {
        Integer night = computeNights(c.checkIn(), c.checkOut());
        // HotelSearchRequest's compact constructor defaults nationality/currency/culture/childAges
        // when null, so passing nulls here is safe.
        return new HotelSearchRequest(
                c.location(),      // destination
                c.checkIn(),       // checkIn (YYYY-MM-DD)
                night,             // night = checkOut - checkIn
                c.adults(),        // adult
                c.childAges(),     // childAges
                c.nationality(),   // nationality
                c.currency(),      // currency
                null               // culture (defaults to tr-TR)
        );
    }

    /**
     * @return positive night count, or null when either date is missing/unparseable or the
     *         span is non-positive — so the hotel module surfaces it as a missing parameter.
     */
    private Integer computeNights(String checkIn, String checkOut) {
        if (checkIn == null || checkOut == null) {
            return null;
        }
        try {
            long nights = ChronoUnit.DAYS.between(LocalDate.parse(checkIn), LocalDate.parse(checkOut));
            return nights > 0 ? (int) nights : null;
        } catch (DateTimeParseException e) {
            return null;
        }
    }
}
