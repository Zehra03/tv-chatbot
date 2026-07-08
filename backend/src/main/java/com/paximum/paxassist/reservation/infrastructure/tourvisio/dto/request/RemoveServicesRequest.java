package com.paximum.paxassist.reservation.infrastructure.tourvisio.dto.request;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * RemoveServices — remove services from an open transaction basket.
 *
 * <p>TODO: confirm real payload/response shape from TourVisio docs. BEST-EFFORT guess,
 * not verified.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record RemoveServicesRequest(
        @JsonProperty("TransactionId") String transactionId,
        @JsonProperty("ServiceIds") List<String> serviceIds) {
}
