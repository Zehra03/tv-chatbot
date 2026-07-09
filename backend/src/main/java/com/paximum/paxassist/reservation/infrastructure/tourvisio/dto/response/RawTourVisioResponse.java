package com.paximum.paxassist.reservation.infrastructure.tourvisio.dto.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * Placeholder response for the booking-transaction endpoints whose full body
 * schema is NOT yet confirmed (BeginTransaction, AddServices, RemoveServices,
 * SetReservationInfo, CommitTransaction, GetReservationDetail, GetReservationList).
 *
 * <p>The {@link #header()} envelope IS confirmed and typed; {@code body} is kept as
 * a raw {@link JsonNode} on purpose so we don't fabricate field names for a payload
 * we haven't seen. Replace {@code body} with a typed DTO once the real shapes are
 * confirmed from TourVisio docs.
 *
 * <p>TODO: confirm real payload/response shape from TourVisio docs and replace the
 * raw {@code body} with a typed record per endpoint.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record RawTourVisioResponse(
        TourVisioResponseHeader header,
        JsonNode body) implements TourVisioResponse {
}
