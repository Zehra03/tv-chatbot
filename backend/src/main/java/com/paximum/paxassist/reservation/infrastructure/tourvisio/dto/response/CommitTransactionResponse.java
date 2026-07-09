package com.paximum.paxassist.reservation.infrastructure.tourvisio.dto.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Response of {@code committransaction} — the point-of-no-return purchase result:
 * {@code header{...}, body{ reservationNumber, encryptedReservationNumber, transactionId }}.
 *
 * <p>{@code reservationNumber} (e.g. {@code "RC002576"}) is the external booking reference that
 * ticket 3 must persist (as {@code external_reservation_number}). {@code encryptedReservationNumber}
 * is TourVisio's opaque handle for the same booking.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CommitTransactionResponse(
        TourVisioResponseHeader header,
        Body body) implements TourVisioResponse {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Body(
            String reservationNumber,
            String encryptedReservationNumber,
            String transactionId) {
    }
}
