package com.paximum.paxassist.flight.infrastructure.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Request body for TourVisio's departure/arrival autocomplete
 * ({@code /api/productservice/get{departure,arrival}autocomplete}). Turns a free-text place name
 * (e.g. {@code "Antalya"}) into location suggestions whose {@code id} feeds the price-search
 * {@code DepartureLocations}/{@code ArrivalLocations}. {@code productType} is 3 for flights.
 */
public record TourVisioAutocompleteRequest(
        @JsonProperty("ProductType") int productType,
        @JsonProperty("Query") String query,
        @JsonProperty("Culture") String culture) {
}
