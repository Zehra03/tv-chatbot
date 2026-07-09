package com.paximum.paxassist.reservation.infrastructure.tourvisio.result;

import com.paximum.paxassist.reservation.infrastructure.tourvisio.dto.response.TourVisioResponseHeader;

/**
 * Outcome of a single TourVisio booking-service call, split into four distinct
 * cases so the caller (ticket 3's service layer) can react differently — they are
 * deliberately NOT collapsed into one exception:
 *
 * <ul>
 *   <li>{@link Success} — HTTP 2xx and {@code header.success == true}.</li>
 *   <li>{@link BusinessFailure} — a clean TourVisio rejection ({@code header.success == false});
 *       the operation definitively did not happen. Safe to treat as a normal negative result.</li>
 *   <li>{@link TechnicalFailure} — a definite transport/technical failure (connection refused,
 *       4xx bad request, retries exhausted). The operation definitively did not take effect.</li>
 *   <li>{@link UnknownOutcome} — an <em>ambiguous</em> failure (timeout / network drop / 5xx) on a
 *       non-idempotent, point-of-no-return call ({@code commitTransaction} / {@code cancelReservation}).
 *       The request may or may not have been applied on TourVisio's side. The caller MUST verify via
 *       {@code getReservationDetail} before assuming failure, and MUST NOT blindly retry.</li>
 * </ul>
 *
 * @param <T> the parsed success body type (always a {@code TourVisioResponse})
 */
public sealed interface TourVisioCallResult<T>
        permits TourVisioCallResult.Success,
                TourVisioCallResult.BusinessFailure,
                TourVisioCallResult.TechnicalFailure,
                TourVisioCallResult.UnknownOutcome {

    /** HTTP 2xx and {@code header.success == true}. */
    record Success<T>(T body) implements TourVisioCallResult<T> {}

    /**
     * TourVisio returned a well-formed envelope with {@code header.success == false}.
     * The full header (including {@code messages[].code}) is carried so the caller can
     * branch on the granular error code.
     */
    record BusinessFailure<T>(TourVisioResponseHeader header) implements TourVisioCallResult<T> {
        public String primaryCode() {
            return header == null ? null : header.primaryCode();
        }
    }

    /**
     * A definite technical failure — the call did not take effect on TourVisio's side
     * (e.g. connection refused, 4xx, or retries exhausted for a retryable call).
     */
    record TechnicalFailure<T>(String description, Throwable cause) implements TourVisioCallResult<T> {}

    /**
     * Ambiguous outcome of a non-idempotent point-of-no-return call. DO NOT auto-retry:
     * verify actual state via {@code getReservationDetail} using {@link #reservationRef()}
     * before assuming the purchase/cancellation failed.
     */
    record UnknownOutcome<T>(String reservationRef, String description, Throwable cause)
            implements TourVisioCallResult<T> {}
}
