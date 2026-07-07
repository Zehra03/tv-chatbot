package com.paximum.paxassist.reservation.infrastructure.tourvisio.dto.request;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * GetReservationList — read-only listing.
 *
 * <p>TODO: confirm real payload/response shape from TourVisio docs. The real filter set
 * (date range, status, paging, agency scoping, ...) is unknown; these two fields are a
 * BEST-EFFORT placeholder and are omitted from the JSON when null. Not verified.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record GetReservationListRequest(
        @JsonProperty("beginDate") String beginDate,
        @JsonProperty("endDate") String endDate) {

    /** An empty filter (send all defaults). */
    public static GetReservationListRequest empty() {
        return new GetReservationListRequest(null, null);
    }
}
