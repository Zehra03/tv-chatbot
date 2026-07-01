package com.paximum.paxassist.flight.infrastructure.dto.response;

import java.util.List;

public record TourVisioFlightResult(
        String id,
        int stopCount,
        List<TourVisioFlightItem> items,
        List<TourVisioOffer> offers) {
}