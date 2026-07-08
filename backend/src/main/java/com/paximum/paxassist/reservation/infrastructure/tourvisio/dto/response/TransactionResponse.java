package com.paximum.paxassist.reservation.infrastructure.tourvisio.dto.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * Shared response envelope for the transaction-building steps — BeginTransaction (both
 * variants), AddServices, RemoveServices and SetReservationInfo all return this shape:
 * {@code header{...}, body{ transactionId, expiresOn, reservationData{...}, status, transactionType }}.
 *
 * <p>{@code reservationData} (travellers[], reservationInfo{}, services[], paymentDetail{}) has no
 * fully documented internal shape yet, so it is kept as a raw {@link JsonNode}.
 * TODO: type {@code reservationData} once its nested schema is confirmed.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TransactionResponse(
        TourVisioResponseHeader header,
        Body body) implements TourVisioResponse {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Body(
            String transactionId,
            String expiresOn,
            JsonNode reservationData,
            Integer status,
            Integer transactionType) {
    }
}
