package com.paximum.paxassist.flight.infrastructure.dto.response;

import java.util.List;

public record TourVisioFlightResult(
        String id,
        List<TourVisioFlightItem> items,
        TourVisioOffer offer) {
}