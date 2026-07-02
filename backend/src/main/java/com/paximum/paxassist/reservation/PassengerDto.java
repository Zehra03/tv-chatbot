package com.paximum.paxassist.reservation;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record PassengerDto(
        @NotBlank String firstName,
        @NotBlank String lastName,
        @NotNull @Pattern(regexp = "ADULT|CHILD|INFANT", message = "ADULT, CHILD veya INFANT olmalıdır")
        String passengerType,
        @Min(0) Integer age,
        String nationality
) {}
