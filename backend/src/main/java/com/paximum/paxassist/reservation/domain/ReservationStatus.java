package com.paximum.paxassist.reservation.domain;

import java.util.Locale;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Lifecycle state of a reservation.
 *
 * <p>Persisted lowercase in {@code reservations.status}
 * (CHECK: pending/confirmed/cancelled/failed) via
 * {@link com.paximum.paxassist.reservation.domain.converter.ReservationStatusConverter}.
 *
 * <p>Serialized to/from JSON lowercase ({@code "confirmed"}) so the wire contract matches the DB
 * values and the frontend union types.
 */
public enum ReservationStatus {
    PENDING,
    CONFIRMED,
    CANCELLED,
    FAILED;

    @JsonValue
    public String toJson() {
        return name().toLowerCase(Locale.ROOT);
    }

    @JsonCreator
    public static ReservationStatus fromJson(String value) {
        return value == null ? null : valueOf(value.trim().toUpperCase(Locale.ROOT));
    }
}
