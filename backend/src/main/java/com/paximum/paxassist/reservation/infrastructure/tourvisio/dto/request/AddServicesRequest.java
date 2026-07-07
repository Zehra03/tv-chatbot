package com.paximum.paxassist.reservation.infrastructure.tourvisio.dto.request;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * AddServices — add offers to an open transaction basket. CONFIRMED payload:
 * {@code { "transactionId": "...", "offers": [{ "offerId": "...", "travellers": ["1","2"] }],
 * "currency": "EUR", "culture": "en-US" }}.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AddServicesRequest(
        @JsonProperty("transactionId") String transactionId,
        @JsonProperty("offers") List<Offer> offers,
        @JsonProperty("currency") String currency,
        @JsonProperty("culture") String culture) {

    /** One offer being added, with the traveller ids it applies to. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Offer(
            @JsonProperty("offerId") String offerId,
            @JsonProperty("travellers") List<String> travellers) {
    }
}
