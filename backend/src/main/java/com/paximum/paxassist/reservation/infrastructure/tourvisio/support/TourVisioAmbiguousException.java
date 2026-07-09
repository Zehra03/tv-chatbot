package com.paximum.paxassist.reservation.infrastructure.tourvisio.support;

/**
 * Raised for an <em>ambiguous</em> transport failure — a timeout, dropped connection, or
 * 5xx — where the request may or may not have been processed by TourVisio.
 *
 * <p>For idempotent/read-only calls this is safely retried. For non-idempotent
 * point-of-no-return calls (commitTransaction / cancelReservation) it must surface as
 * {@code UnknownOutcome} and NOT be auto-retried.
 */
public class TourVisioAmbiguousException extends RuntimeException {

    public TourVisioAmbiguousException(String message, Throwable cause) {
        super(message, cause);
    }
}
