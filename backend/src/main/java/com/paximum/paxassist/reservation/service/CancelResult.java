package com.paximum.paxassist.reservation.service;

import com.paximum.paxassist.reservation.domain.ReservationStatus;

/** Outcome of a cancellation attempt, mapped by the controller to an HTTP response. */
public sealed interface CancelResult
        permits CancelResult.Cancelled,
                CancelResult.NotFound,
                CancelResult.NotCancellable,
                CancelResult.TourVisioRejected,
                CancelResult.NotReadyYet,
                CancelResult.TourVisioUnavailable,
                CancelResult.OutcomeUnknown {

    /** TourVisio cancelled the booking; local status updated to {@link #newStatus()}. */
    record Cancelled(ReservationStatus newStatus) implements CancelResult {}

    /** No reservation with the given local id. */
    record NotFound() implements CancelResult {}

    /** The reservation has no TourVisio external reference to cancel against. */
    record NotCancellable(String reason) implements CancelResult {}

    /** TourVisio rejected the cancellation ({@code header.success == false}). */
    record TourVisioRejected(String code, String message) implements CancelResult {}

    /**
     * TourVisio refused the void because the sale transaction has not settled yet at the payment-gateway
     * step — a transient state right after booking. Nothing changed; the user can retry in a few minutes.
     * Distinct from {@link TourVisioRejected} (a permanent refusal) so the UI can say "try again shortly"
     * instead of "cancellation failed".
     */
    record NotReadyYet(String message) implements CancelResult {}

    /** Technical failure calling TourVisio; local status unchanged. */
    record TourVisioUnavailable(String description) implements CancelResult {}

    /** Ambiguous outcome (timeout/network) — the cancellation may or may not have applied; verify. */
    record OutcomeUnknown(String description) implements CancelResult {}
}
