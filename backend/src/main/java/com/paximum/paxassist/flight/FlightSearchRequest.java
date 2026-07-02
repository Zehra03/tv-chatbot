package com.paximum.paxassist.flight;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.List;

public record FlightSearchRequest(
        @NotBlank String origin,
        @NotBlank String destination,
        @NotNull LocalDate departureDate,
        LocalDate returnDate,
        @NotNull @Min(1) Integer adults,
        Integer children,
        List<Integer> childAges,
        String cabinClass,
        String nationality,
        String currency
) {}
