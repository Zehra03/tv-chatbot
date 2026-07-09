package com.paximum.paxassist.reservation.infrastructure.tourvisio.dto.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * {@code reason} of a cancel-penalty entry. The {@link #id()} is the value the
 * caller passes back as the {@code reason} of a {@code cancelreservation} request.
 *
 * <p>Confirmed to be a {@link String} (e.g. {@code "2"}, {@code "6"}) even though it looks
 * numeric — it is echoed back verbatim as the {@code reason} field of {@code cancelreservation}.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CancellationReason(
        String id,
        String name,
        String comment) {
}
