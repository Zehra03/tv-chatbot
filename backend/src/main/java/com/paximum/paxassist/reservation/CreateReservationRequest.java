package com.paximum.paxassist.reservation;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.util.List;

public record CreateReservationRequest(
        @NotBlank @Pattern(regexp = "HOTEL|FLIGHT", message = "HOTEL veya FLIGHT olmalıdır")
        String productType,
        @NotBlank String selectedProductId,
        @NotNull @NotEmpty List<@Valid PassengerDto> passengers
) {}
