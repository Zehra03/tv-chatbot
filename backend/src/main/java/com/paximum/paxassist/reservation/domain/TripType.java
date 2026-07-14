package com.paximum.paxassist.reservation.domain;

import java.util.Locale;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Flight itinerary shape for a booked flight snapshot.
 *
 * <p>Persisted snake_case in {@code flight_reservation_details.trip_type}
 * (CHECK: one_way/round_trip) via
 * {@link com.paximum.paxassist.reservation.domain.converter.TripTypeConverter}.
 *
 * <p>Intentionally a reservation-module copy of
 * {@code com.paximum.paxassist.flight.domain.TripType}: the reservation snapshot
 * must not depend on the flight module's internals (module-boundary rule).
 *
 * <p>Serialized to/from JSON snake_case ({@code "one_way"}) so the wire contract matches the DB
 * values and the frontend union types ({@code name().toLowerCase()} keeps the underscore).
 */
public enum TripType {
    ONE_WAY,
    ROUND_TRIP;

    @JsonValue
    public String toJson() {
        return name().toLowerCase(Locale.ROOT);
    }

    @JsonCreator
    public static TripType fromJson(String value) {
        return value == null ? null : valueOf(value.trim().toUpperCase(Locale.ROOT));
    }
}
