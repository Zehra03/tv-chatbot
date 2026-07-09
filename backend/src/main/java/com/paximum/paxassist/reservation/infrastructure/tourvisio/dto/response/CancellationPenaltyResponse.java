package com.paximum.paxassist.reservation.infrastructure.tourvisio.dto.response;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Full response of {@code getcancellationpenalty}: the confirmed
 * {@link TourVisioResponseHeader} envelope plus {@code body.cancelPenalties[]}.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CancellationPenaltyResponse(
        TourVisioResponseHeader header,
        Body body) implements TourVisioResponse {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Body(List<CancelPenalty> cancelPenalties) {
    }
}
