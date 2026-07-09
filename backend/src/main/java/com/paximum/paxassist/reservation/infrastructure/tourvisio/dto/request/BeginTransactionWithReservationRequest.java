package com.paximum.paxassist.reservation.infrastructure.tourvisio.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * BeginTransaction — "with existing reservation" variant.
 *
 * <p>TODO: confirm real payload/response shape from TourVisio docs. BEST-EFFORT guess,
 * not verified (see decisions note).
 */
public record BeginTransactionWithReservationRequest(
        @JsonProperty("ReservationNumber") String reservationNumber) {
}
