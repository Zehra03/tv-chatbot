package com.paximum.paxassist.flight.dto;

import jakarta.validation.constraints.Min;

public record PassengerCountDto(
        @Min(1) int adults,
        @Min(0) int children,
        @Min(0) int infants) {
}