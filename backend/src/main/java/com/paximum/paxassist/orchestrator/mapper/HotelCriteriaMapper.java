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
 * module wants a {@code night} count. When both dates are present we compute the day span;
 * otherwise we fall back to an explicit {@code nights} count (the user answered "5 gece"
 * instead of a checkout date). If neither yields a positive night count we pass {@code null}
 * through and let the hotel module report it as a missing parameter — completeness stays a
 * single source of truth in the hotel module.
 *
 * <p>Not yet mapped (Phase 1): {@code rooms}, {@code stars}, {@code boardType} — the current
 * {@code HotelSearchRequest} has no fields for them; {@code stars}/{@code boardType} are applied
 * as post-search filters via the FILTER intent instead.
 */
@Component
public class HotelCriteriaMapper {

    private final GeoCountryResolver geoCountry;

    public HotelCriteriaMapper(GeoCountryResolver geoCountry) {
        this.geoCountry = geoCountry;
    }

    public HotelSearchRequest toRequest(SlotCriteria c) {
        Integer night = computeNights(c.checkIn(), c.checkOut(), c.nights());
        // HotelSearchRequest's compact constructor defaults nationality/culture/childAges when null,
        // so passing nulls here is safe. The currency is never asked for: it follows from where the
        // request came from unless the user named one (and so is never null by the time it lands).
        return new HotelSearchRequest(
                c.location(),      // destination
                c.checkIn(),       // checkIn (YYYY-MM-DD)
                night,             // night = (checkOut - checkIn) or explicit nights count
                c.adults(),        // adult
                c.childAges(),     // childAges
                c.nationality(),   // nationality
                CurrencyByCountry.resolve(c.currency(), geoCountry.currentCountry().orElse(null)),
                null               // culture (defaults to tr-TR)
        );
    }

    /**
     * Resolves the night count. A checkout date (span from check-in) is the most precise signal
     * and wins when it yields a positive span; otherwise we fall back to the explicit {@code nights}
     * count the user gave ("5 gece"). Returns null when neither is usable — so the hotel module
     * surfaces it as a missing parameter.
     */
    private Integer computeNights(String checkIn, String checkOut, Integer nights) {
        Integer fromDates = spanBetween(checkIn, checkOut);
        if (fromDates != null) {
            return fromDates;
        }
        return (nights != null && nights > 0) ? nights : null;
    }

    /**
     * @return positive night span between the two dates, or null when either date is
     *         missing/unparseable or the span is non-positive.
     */
    private Integer spanBetween(String checkIn, String checkOut) {
        if (checkIn == null || checkOut == null) {
            return null;
        }
        try {
            long span = ChronoUnit.DAYS.between(LocalDate.parse(checkIn), LocalDate.parse(checkOut));
            return span > 0 ? (int) span : null;
        } catch (DateTimeParseException e) {
            return null;
        }
    }
}
