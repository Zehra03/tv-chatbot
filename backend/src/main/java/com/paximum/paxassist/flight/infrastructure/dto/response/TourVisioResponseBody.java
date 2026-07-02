package com.paximum.paxassist.flight.infrastructure.dto.response;

import java.util.List;

public record TourVisioResponseBody(String searchId, List<TourVisioFlightResult> flights) {
}