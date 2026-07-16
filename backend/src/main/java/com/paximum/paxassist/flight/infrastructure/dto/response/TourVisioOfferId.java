package com.paximum.paxassist.flight.infrastructure.dto.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * The booking token for one offer, valid only within a given group of combinable offers: a
 * round-trip offer carries one {@code offerId} per {@code groupKey} it belongs to, and the id to
 * book with is the one whose group key the outbound and the return agree on.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TourVisioOfferId(String groupKey, String offerId) {
}
