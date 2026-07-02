package com.paximum.paxassist.reservation;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record PassengerDto(
        @NotBlank String firstName,
        @NotBlank String lastName,
        @NotNull String passengerType,
        Integer age,
        String nationality
) {}
