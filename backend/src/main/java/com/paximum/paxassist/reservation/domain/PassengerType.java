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
 * <p>The age bands are enforced in {@code PreviewReservationCommand}:
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
    ADULT,
    CHILD,
    INFANT;

    @JsonValue
    public String toJson() {
        return name().toLowerCase(Locale.ROOT);
    }

    @JsonCreator
    public static PassengerType fromJson(String value) {
        return value == null ? null : valueOf(value.trim().toUpperCase(Locale.ROOT));
    }
}
