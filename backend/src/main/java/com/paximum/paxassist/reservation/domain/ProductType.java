package com.paximum.paxassist.reservation.domain;

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
 */
public enum ProductType {
    HOTEL,
    FLIGHT,
    COMBINED
}
