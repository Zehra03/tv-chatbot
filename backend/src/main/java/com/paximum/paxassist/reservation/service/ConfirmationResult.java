package com.paximum.paxassist.reservation.service;

import java.math.BigDecimal;
import java.util.List;

/**
 * Outcome of {@code confirmReservation} / {@code confirmReservationAfterWarning}. Distinct states so
 * the caller (ticket 4's controller) can map each to the right response — not collapsed into a boolean
 * or a single exception.
 */
public sealed interface ConfirmationResult
        permits ConfirmationResult.Confirmed,
                ConfirmationResult.NeedsUserConfirmation,
                ConfirmationResult.PreviewExpired,
                ConfirmationResult.OwnershipMismatch,
                ConfirmationResult.DuplicateInProgress,
                ConfirmationResult.TourVisioRejected,
                ConfirmationResult.TourVisioUnavailable,
                ConfirmationResult.CommitOutcomeUnknown,
                ConfirmationResult.OrphanedBooking,
                ConfirmationResult.PriceMismatch {

    /** Purchase committed AND persisted. Happy path. */
    record Confirmed(Long reservationId, String reservationNumber, String externalReservationNumber)
            implements ConfirmationResult {}

    /**
     * setReservationInfo returned a {@code messageType == 4} warning (e.g. DuplicateReservationFound).
     * NOT committed. The user must acknowledge and call {@code confirmReservationAfterWarning} with
     * {@link #confirmationToken()} to proceed to commit.
     */
    record NeedsUserConfirmation(String confirmationToken, List<String> warnings) implements ConfirmationResult {}

    /** No live snapshot for the given id (expired, unknown, or already consumed). "Start again." */
    record PreviewExpired() implements ConfirmationResult {}

    /** The snapshot's owner id does not match the requesting user. (Ownership enforcement finalized in ticket 5.) */
    record OwnershipMismatch() implements ConfirmationResult {}

    /** Lost the atomic claim race — another concurrent/duplicate confirm already took this preview. */
    record DuplicateInProgress() implements ConfirmationResult {}

    /** A TourVisio step returned a clean business rejection ({@code header.success == false}); no purchase happened. */
    record TourVisioRejected(String step, String code, String message) implements ConfirmationResult {}

    /** A TourVisio step failed technically (timeout/5xx/network, retries exhausted) before commit; no purchase happened. */
    record TourVisioUnavailable(String step, String description) implements ConfirmationResult {}

    /**
     * commitTransaction returned an ambiguous outcome (timeout/network) — the purchase MAY have
     * succeeded. Not persisted; must be reconciled by verifying via {@code getReservationDetail}.
     */
    record CommitOutcomeUnknown(String reference, String description) implements ConfirmationResult {}

    /**
     * commitTransaction SUCCEEDED but the subsequent ResDB persist failed — a real purchase exists on
     * TourVisio with no local record. Logged at highest severity + flagged for manual reconciliation.
     */
    record OrphanedBooking(String externalReservationNumber, String description) implements ConfirmationResult {}

    /**
     * The amount TourVisio priced the transaction at does not match the amount the client declared, so
     * the flow was aborted <b>before</b> commit — no purchase happened and nothing was persisted.
     *
     * <p>Covers both causes with one outcome, because the client's declared amount is not evidence of
     * either: a genuine price change between search and confirm, and a tampered {@code totalAmount}
     * aimed at writing a fake price into the DB and "Rezervasyonlarım". Carrying both amounts lets the
     * UI show old vs new and ask the user to preview again.
     */
    record PriceMismatch(BigDecimal declaredAmount, BigDecimal actualAmount, String currency)
            implements ConfirmationResult {}
}
