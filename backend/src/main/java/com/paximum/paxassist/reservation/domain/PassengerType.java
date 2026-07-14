package com.paximum.paxassist.reservation.domain;

import java.util.Locale;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Passenger age category.
 *
 * <p>Persisted lowercase in {@code passengers.passenger_type}
 * (CHECK: adult/child) via
 * {@link com.paximum.paxassist.reservation.domain.converter.PassengerTypeConverter}.
 *
 * <p>Serialized to/from JSON lowercase ({@code "adult"}) so the wire contract matches the DB values
 * and the frontend union types.
 */
public enum PassengerType {
    ADULT,
    CHILD;

    @JsonValue
    public String toJson() {
        return name().toLowerCase(Locale.ROOT);
    }

    @JsonCreator
    public static PassengerType fromJson(String value) {
        return value == null ? null : valueOf(value.trim().toUpperCase(Locale.ROOT));
    }
}
