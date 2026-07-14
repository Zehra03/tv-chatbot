package com.paximum.paxassist.reservation.domain;

import java.util.Locale;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Discriminator for which detail table(s) hold the booked snapshot.
 *
 * <p>Persisted lowercase in {@code reservations.product_type}
 * (CHECK: hotel/flight/combined) via
 * {@link com.paximum.paxassist.reservation.domain.converter.ProductTypeConverter}.
 *
 * <p>{@code COMBINED} covers package bookings (both hotel and flight present).
 * The value should be <em>derived</em> from which details exist rather than
 * trusted from client input — see {@link Reservation#deriveProductType()}.
 *
 * <p>Serialized to/from JSON lowercase ({@code "hotel"}) so the wire contract matches the DB values
 * and the frontend union types (no case-mapping needed on the client).
 */
public enum ProductType {
    HOTEL,
    FLIGHT,
    COMBINED;

    @JsonValue
    public String toJson() {
        return name().toLowerCase(Locale.ROOT);
    }

    @JsonCreator
    public static ProductType fromJson(String value) {
        return value == null ? null : valueOf(value.trim().toUpperCase(Locale.ROOT));
    }
}
