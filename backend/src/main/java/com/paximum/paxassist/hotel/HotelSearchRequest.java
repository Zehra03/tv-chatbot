package com.paximum.paxassist.hotel;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.List;

public record HotelSearchRequest(
        @NotBlank String location,
        @NotNull LocalDate checkIn,
        @NotNull LocalDate checkOut,
        @NotNull @Min(1) Integer adults,
        Integer children,
        List<Integer> childAges,
        @NotNull @Min(1) Integer rooms,
        Integer stars,
        String boardType,
        String nationality,
        String currency,
        String sortBy
) {}
