package com.paximum.paxassist.flight.infrastructure.dto.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * One end of a flight leg. {@code city} is the city the {@code airport} sits in (e.g. airport SAW →
 * city Istanbul) — used to label result cards with a human place name under the airport code.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TourVisioFlightPoint(TourVisioAirport airport, String date, TourVisioCity city) {
}