package com.paximum.paxassist.reservation.infrastructure.tourvisio.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * GetReservationDetail — read-only lookup, also used to VERIFY the real state after an
 * {@code UnknownOutcome} from commit/cancel.
 *
 * <p>TODO: confirm real payload/response shape from TourVisio docs. camelCase key is a
 * BEST-EFFORT guess by analogy with the confirmed cancellation payloads, not verified.
 */
public record GetReservationDetailRequest(
        @JsonProperty("reservationNumber") String reservationNumber) {
}
