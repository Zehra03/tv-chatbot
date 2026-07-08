package com.paximum.paxassist.reservation.web.dto;

import java.util.List;

import jakarta.validation.constraints.NotBlank;

/**
 * Body of PATCH /api/v1/reservations/{id}/cancel.
 *
 * @param reason     the {@code reason.id} the user picked from the {@code cancellationOptions} shown on
 *                   the detail screen
 * @param serviceIds optional subset of services to cancel
 */
public record CancelRequest(
        @NotBlank String reason,
        List<String> serviceIds) {
}
