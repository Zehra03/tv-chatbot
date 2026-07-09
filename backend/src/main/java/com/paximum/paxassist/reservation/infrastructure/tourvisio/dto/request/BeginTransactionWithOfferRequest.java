package com.paximum.paxassist.reservation.infrastructure.tourvisio.dto.request;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * BeginTransaction — "with offer" variant. CONFIRMED payload:
 * {@code { "offerIds": [...], "currency": "EUR", "culture": "en-US" }} (camelCase).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record BeginTransactionWithOfferRequest(
        @JsonProperty("offerIds") List<String> offerIds,
        @JsonProperty("currency") String currency,
        @JsonProperty("culture") String culture) {
}
