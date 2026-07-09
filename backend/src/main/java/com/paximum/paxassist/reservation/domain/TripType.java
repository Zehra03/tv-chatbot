package com.paximum.paxassist.reservation.domain;

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
 */
public enum TripType {
    ONE_WAY,
    ROUND_TRIP
}
