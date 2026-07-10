package com.paximum.paxassist.flight.infrastructure.dto.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * The {@code city} block a TourVisio flight point carries alongside the airport (e.g.
 * {@code {"id":"IST","name":"Istanbul"}}). Only id/name are needed to label a result card with the
 * city the airport code belongs to; provider/geo flags are ignored.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TourVisioCity(String id, String name) {
}
