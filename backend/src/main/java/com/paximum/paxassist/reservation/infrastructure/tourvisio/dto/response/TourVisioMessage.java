package com.paximum.paxassist.reservation.infrastructure.tourvisio.dto.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * One entry of {@link TourVisioResponseHeader#messages()}.
 *
 * <p>{@code code} (e.g. {@code "OperationCompleted"}) is the granular error/status
 * code to branch on. {@code id} and {@code messageType} are confirmed integers
 * (e.g. id {@code 10000000}, messageType {@code 2}/{@code 4}); modelled as nullable
 * {@link Integer}s.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TourVisioMessage(
        Integer id,
        String code,
        Integer messageType,
        String message) {
}
