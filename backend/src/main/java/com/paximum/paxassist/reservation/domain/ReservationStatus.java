package com.paximum.paxassist.reservation.domain;

/**
 * Lifecycle state of a reservation.
 *
 * <p>Persisted lowercase in {@code reservations.status}
 * (CHECK: pending/confirmed/cancelled/failed) via
 * {@link com.paximum.paxassist.reservation.domain.converter.ReservationStatusConverter}.
 */
public enum ReservationStatus {
    PENDING,
    CONFIRMED,
    CANCELLED,
    FAILED
}
