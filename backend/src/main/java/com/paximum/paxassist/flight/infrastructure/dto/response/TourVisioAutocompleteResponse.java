package com.paximum.paxassist.flight.infrastructure.dto.response;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * TourVisio autocomplete response. Each {@code item} carries either a {@code city} (type 1, "all
 * airports") or an {@code airport} (type 3); both expose an IATA-style {@code id} that the flight
 * price search accepts as a location id. Unknown fields (geolocation, provider, header messages)
 * are ignored.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TourVisioAutocompleteResponse(
        TourVisioResponseHeader header,
        Body body) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Body(List<Item> items) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Item(int type, City city, Airport airport) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record City(String id, String name) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Airport(String id, String code, String name) {
    }
}
