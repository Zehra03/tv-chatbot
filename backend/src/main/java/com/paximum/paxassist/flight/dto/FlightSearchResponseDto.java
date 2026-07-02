package com.paximum.paxassist.flight.dto;

import java.util.List;

public record FlightSearchResponseDto(
        FlightSearchStatus status,
        List<String> missingFields,
        List<FlightProductDto> results) {
}