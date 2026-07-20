package com.paximum.paxassist.flight.dto;

import java.time.LocalDate;

import com.paximum.paxassist.flight.domain.TripType;

import jakarta.validation.Valid;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.Size;

public record FlightSearchRequestDto(
        String origin,
        String destination,
        @FutureOrPresent @com.paximum.paxassist.validator.MaxSearchDate LocalDate departDate,
        @FutureOrPresent @com.paximum.paxassist.validator.MaxSearchDate LocalDate returnDate,
        TripType tripType,
        @Valid PassengerCountDto passengers,
        @Size(min = 3, max = 3) String currency,
        Boolean nonstop,
        String preferredAirline) {
}