package com.paximum.paxassist.reservation.infrastructure.tourvisio.client;

import java.util.List;

import com.paximum.paxassist.reservation.infrastructure.tourvisio.dto.request.AddServicesRequest;
import com.paximum.paxassist.reservation.infrastructure.tourvisio.dto.request.BeginTransactionWithOfferRequest;
import com.paximum.paxassist.reservation.infrastructure.tourvisio.dto.request.BeginTransactionWithReservationRequest;
import com.paximum.paxassist.reservation.infrastructure.tourvisio.dto.request.CommitTransactionRequest;
import com.paximum.paxassist.reservation.infrastructure.tourvisio.dto.request.RemoveServicesRequest;
import com.paximum.paxassist.reservation.infrastructure.tourvisio.dto.request.SetReservationInfoRequest;
import com.paximum.paxassist.reservation.infrastructure.tourvisio.dto.response.CancelReservationResponse;
import com.paximum.paxassist.reservation.infrastructure.tourvisio.dto.response.CancellationPenaltyResponse;
import com.paximum.paxassist.reservation.infrastructure.tourvisio.dto.response.CommitTransactionResponse;
import com.paximum.paxassist.reservation.infrastructure.tourvisio.dto.response.RawTourVisioResponse;
import com.paximum.paxassist.reservation.infrastructure.tourvisio.dto.response.TransactionResponse;
import com.paximum.paxassist.reservation.infrastructure.tourvisio.result.TourVisioCallResult;

/**
 * Thin client over the TourVisio {@code bookingservice} endpoints. It only calls TourVisio
 * and maps the response into a {@link TourVisioCallResult}; it knows nothing about
 * {@code Reservation} entities or repositories (that orchestration is ticket 3).
 *
 * <p><b>Retry policy is intentionally non-uniform</b> (see the impl):
 * <ul>
 *   <li>read-only lookups and the basket-building steps (begin/add/remove/setInfo) retry on
 *       ambiguous timeout — they don't finalize a real-world transaction;</li>
 *   <li>{@link #commitTransaction} and {@link #cancelReservation} are non-idempotent
 *       points-of-no-return: they are NEVER auto-retried on an ambiguous failure and instead
 *       return {@link TourVisioCallResult.UnknownOutcome} so the caller verifies actual state
 *       via {@link #getReservationDetail} before assuming failure.</li>
 * </ul>
 *
 * <p>Booking-flow request/response shapes are best-effort and marked TODO on their DTOs;
 * the cancellation pair is fully implemented against confirmed payloads.
 */
public interface TourVisioBookingClient {

    // --- Basket-building steps (retryable on ambiguous timeout) ------------------------------

    TourVisioCallResult<TransactionResponse> beginTransactionWithOffer(BeginTransactionWithOfferRequest request);

    TourVisioCallResult<TransactionResponse> beginTransactionWithReservation(BeginTransactionWithReservationRequest request);

    TourVisioCallResult<TransactionResponse> addServices(AddServicesRequest request);

    TourVisioCallResult<TransactionResponse> removeServices(RemoveServicesRequest request);

    /**
     * Attaches traveller/customer info. NOTE: even on {@code Success} the response header may carry a
     * {@code messageType == 4} warning (e.g. {@code DuplicateReservationFound}); callers must check
     * {@code header.requiresConfirmation()} and surface it for user confirmation rather than treating
     * it as plain success.
     */
    TourVisioCallResult<TransactionResponse> setReservationInfo(SetReservationInfoRequest request);

    // --- Point of no return (NO auto-retry; ambiguous -> UnknownOutcome) ----------------------

    TourVisioCallResult<CommitTransactionResponse> commitTransaction(CommitTransactionRequest request);

    // --- Read-only lookups (retryable) --------------------------------------------------------

    TourVisioCallResult<RawTourVisioResponse> getReservationDetail(String reservationNumber);

    TourVisioCallResult<RawTourVisioResponse> getReservationList();

    // --- Cancellation (fully implemented against confirmed payloads) --------------------------

    TourVisioCallResult<CancellationPenaltyResponse> getCancellationPenalty(String reservationNumber);

    /**
     * Non-idempotent. On an ambiguous failure returns {@link TourVisioCallResult.UnknownOutcome};
     * verify via {@link #getReservationDetail} before assuming the cancellation failed.
     *
     * @param serviceIds optional subset of services to cancel; {@code null} cancels per TourVisio default
     */
    TourVisioCallResult<CancelReservationResponse> cancelReservation(String reservationNumber, String reason, List<String> serviceIds);
}
