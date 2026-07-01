package com.paximum.paxassist.flight.dto;

import java.time.LocalDate;

import com.paximum.paxassist.flight.domain.TripType;

import jakarta.validation.Valid;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record FlightSearchRequestDto(
        @NotBlank String origin,
        @NotBlank String destination,
        @NotNull @FutureOrPresent LocalDate departDate,
        @FutureOrPresent LocalDate returnDate,
        @NotNull TripType tripType,
        @NotNull @Valid PassengerCountDto passengers,
        @NotBlank @Size(min = 3, max = 3) String currency,
        Boolean nonstop,
        String preferredAirline) {
}