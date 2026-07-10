package com.paximum.paxassist.auth.dto;

import jakarta.validation.constraints.NotBlank;

// Body of POST /api/v1/auth/refresh. Matches frontend/src/api/authApi.ts's refresh() call.
public record RefreshRequestDto(
        @NotBlank String refreshToken) {
}
