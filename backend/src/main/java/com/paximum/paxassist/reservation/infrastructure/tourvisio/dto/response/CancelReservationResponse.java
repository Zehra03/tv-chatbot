package com.paximum.paxassist.reservation.infrastructure.tourvisio.dto.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Full response of {@code cancelreservation}: the confirmed
 * {@link TourVisioResponseHeader} envelope plus {@code body.reservationStatus}
 * (an integer status code).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CancelReservationResponse(
        TourVisioResponseHeader header,
        Body body) implements TourVisioResponse {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Body(Integer reservationStatus) {
    }
}
