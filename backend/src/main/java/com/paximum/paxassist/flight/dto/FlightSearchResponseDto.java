package com.paximum.paxassist.flight.dto;

import java.util.List;

public record FlightSearchResponseDto(
        FlightSearchRequestDto criteria,
        List<FlightProductDto> results) {
}