package com.paximum.paxassist.reservation;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record ReservationPreviewRequest(
        @NotBlank String productType,
        @NotBlank String selectedProductId,
        @NotNull @NotEmpty List<@Valid PassengerDto> passengers
) {}
