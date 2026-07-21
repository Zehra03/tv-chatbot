package com.paximum.paxassist.reservation.domain;

import java.util.Locale;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Passenger age category.
 *
 * <p>Persisted lowercase in {@code passengers.passenger_type}
 * (CHECK: adult/child/infant) via
 * {@link com.paximum.paxassist.reservation.domain.converter.PassengerTypeConverter}.
 *
 * <p>Serialized to/from JSON lowercase ({@code "adult"}) so the wire contract matches the DB values
 * and the frontend union types.
 *
 * <p>The age bands live HERE, on the type they describe, and {@code PreviewReservationCommand}
 * validates against them ({@link #matchesAge(int)}) instead of restating the numbers. They were
 * previously spelled out as private constants inside that command, so the enum's documentation and
 * the rule that enforces it could drift apart silently:
 * <ul>
 *   <li>{@link #ADULT} — 18 and over.</li>
 *   <li>{@link #CHILD} — 3 to 17.</li>
 *   <li>{@link #INFANT} — 0 to 2, and <b>flight-only</b>: it is an airline fare type (a lap infant
 *       carried on an adult's ticket, hence at most one per adult). Hotels have no such type — they
 *       model children by exact age and price them from age bands (this is also how the hotel search
 *       already works, via {@code childAges}), so an under-2 in a hotel booking is simply a child.
 *       A booking with no flight therefore cannot carry an INFANT.</li>
 * </ul>
 */
public enum PassengerType {
    // Qualified on purpose: a simple-name reference to a field declared further down is an
    // "illegal forward reference", even for a compile-time constant like MAX_AGE.
    ADULT(18, PassengerType.MAX_AGE),
    CHILD(3, 17),
    INFANT(0, 2);

    /**
     * Upper bound accepted for any stated age. Not a band boundary — a sanity limit on the input, and
     * the value {@code @Max} uses on the traveller's age field (hence a compile-time constant).
     */
    public static final int MAX_AGE = 120;

    private final int minAge;
    private final int maxAge;

    PassengerType(int minAge, int maxAge) {
        this.minAge = minAge;
        this.maxAge = maxAge;
    }

    /** Youngest age (inclusive) that may be booked under this type. */
    public int minAge() {
        return minAge;
    }

    /** Oldest age (inclusive) that may be booked under this type. */
    public int maxAge() {
        return maxAge;
    }

    /** Whether a stated age falls inside this type's band — the single check every layer should use. */
    public boolean matchesAge(int age) {
        return age >= minAge && age <= maxAge;
    }

    @JsonValue
    public String toJson() {
        return name().toLowerCase(Locale.ROOT);
    }

    @JsonCreator
    public static PassengerType fromJson(String value) {
        return value == null ? null : valueOf(value.trim().toUpperCase(Locale.ROOT));
    }
}
