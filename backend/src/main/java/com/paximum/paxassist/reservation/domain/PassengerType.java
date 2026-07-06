package com.paximum.paxassist.reservation.domain;

/**
 * Passenger age category.
 *
 * <p>Persisted lowercase in {@code passengers.passenger_type}
 * (CHECK: adult/child) via
 * {@link com.paximum.paxassist.reservation.domain.converter.PassengerTypeConverter}.
 */
public enum PassengerType {
    ADULT,
    CHILD
}
