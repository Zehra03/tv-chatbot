package com.paximum.paxassist.reservation.infrastructure.tourvisio.dto.request;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Request for {@code cancelreservation}. Confirmed payload:
 * {@code { "reservationNumber": string, "reason": string, "serviceIds": string[] /* optional *&#47; }}.
 *
 * <p>{@code reason} is a {@code cancelPenalties[].reason.id} the caller chose.
 * {@code serviceIds} is optional and omitted from the JSON when {@code null}.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record CancelReservationRequest(
        @JsonProperty("reservationNumber") String reservationNumber,
        @JsonProperty("reason") String reason,
        @JsonProperty("serviceIds") List<String> serviceIds) {
}
