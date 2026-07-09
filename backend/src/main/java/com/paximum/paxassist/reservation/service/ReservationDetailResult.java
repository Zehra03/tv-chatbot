package com.paximum.paxassist.reservation.service;

import java.util.List;

import com.paximum.paxassist.reservation.domain.Reservation;
import com.paximum.paxassist.reservation.infrastructure.tourvisio.dto.response.CancelPenalty;

/**
 * Result of a reservation-detail lookup: the persisted {@link Reservation} plus its live TourVisio
 * cancellation options (empty when the reservation has no external reference or the lookup failed).
 * The controller maps this to the detail response DTO.
 */
public record ReservationDetailResult(
        Reservation reservation,
        List<CancelPenalty> cancellationOptions) {
}
