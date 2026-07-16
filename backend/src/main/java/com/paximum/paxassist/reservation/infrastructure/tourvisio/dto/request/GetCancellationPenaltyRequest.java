package com.paximum.paxassist.reservation.infrastructure.tourvisio.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Request for {@code getcancellationpenalty}. Confirmed payload: {@code { "reservationNumber": string }}.
 * Note the camelCase key — the cancellation endpoints use camelCase, unlike the
 * PascalCase login/pricesearch payloads.
 */
public record GetCancellationPenaltyRequest(
        @JsonProperty("reservationNumber") String reservationNumber) {
}
