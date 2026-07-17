package com.paximum.paxassist.hotel.dto;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.StringJoiner;

public record HotelSearchRequest(
    String destination,
    String checkIn,
    Integer night,
    Integer adult,
    Integer rooms,
    List<Integer> childAges,
    String nationality,
    String currency,
    String culture
) {
    public HotelSearchRequest {
        if (nationality == null) {
            nationality = "TR";
        }
        if (currency == null) {
            currency = "TRY";
        }
        if (culture == null) {
            culture = "tr-TR";
        }
        if (childAges == null) {
            childAges = List.of();
        }
        if (rooms == null || rooms < 1) {
            rooms = 1;
        }
    }

    /**
     * Eight-arg form kept for callers that genuinely have no room count (they mean one room).
     * Prefer the canonical constructor: dropping the room count is what made every multi-room
     * search quote a single room's price.
     */
    public HotelSearchRequest(String destination, String checkIn, Integer night, Integer adult,
                              List<Integer> childAges, String nationality, String currency, String culture) {
        this(destination, checkIn, night, adult, 1, childAges, nationality, currency, culture);
    }

    /** How this search's party splits across rooms — what TourVisio's {@code roomCriteria} needs. */
    public List<RoomParty> roomParties() {
        return RoomParty.distribute(adult, rooms, childAges);
    }

    /**
     * Canonical identity of this search for the {@code hotelSearch} cache.
     *
     * <p>Every field TourVisio prices on must appear here: children and nationality change the quoted
     * price, so leaving them out would serve one guest's price to another (a family's rate to a solo
     * traveller, a DE rate to a TR guest). Child ages are sorted because [5, 10] and [10, 5] are the
     * same party, and the check-in date is normalized to yyyy-MM-dd so "2026-08-01" and "2026-8-1"
     * don't split into two entries holding identical results.
     *
     * <p>{@code rooms} belongs here for exactly that reason — four adults in one room and four adults
     * in two rooms are different products at different prices. Omitting it would let whichever search
     * ran first serve its price to the other.
     *
     * <p>Null-safe on purpose: {@code @Cacheable} builds the key BEFORE the method runs, so an
     * incomplete request (no destination yet) must produce a key rather than throw — otherwise the
     * service's own missing-parameter check could never report back.
     */
    public String cacheKey() {
        StringJoiner key = new StringJoiner("_");
        key.add(destination == null ? "null" : destination.trim().toLowerCase(Locale.ROOT));
        key.add(canonicalCheckIn());
        key.add(String.valueOf(night));
        key.add(String.valueOf(adult));
        key.add(String.valueOf(rooms));
        key.add(canonicalChildAges());
        key.add(nationality.trim().toUpperCase(Locale.ROOT));
        key.add(currency.trim().toUpperCase(Locale.ROOT));
        return key.toString();
    }

    private String canonicalCheckIn() {
        if (checkIn == null || checkIn.isBlank()) {
            return "null";
        }
        try {
            return LocalDate.parse(checkIn.trim()).toString();
        } catch (DateTimeParseException e) {
            // Unparseable dates still need a stable key; the search itself will reject them.
            return checkIn.trim();
        }
    }

    private String canonicalChildAges() {
        List<Integer> sorted = new ArrayList<>(childAges);
        sorted.sort(null);
        return sorted.toString();
    }
}
