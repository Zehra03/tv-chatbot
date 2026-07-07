package com.paximum.paxassist.reservation.infrastructure.tourvisio.dto.response;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * One entry of {@code body.cancelPenalties[]}: the {@link CancellationReason}, the
 * affected {@link CancellationService services}, the overall {@link TourVisioPrice price},
 * and whether the whole entry is cancelable.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CancelPenalty(
        CancellationReason reason,
        List<CancellationService> services,
        TourVisioPrice price,
        Boolean isCancelable) {
}
