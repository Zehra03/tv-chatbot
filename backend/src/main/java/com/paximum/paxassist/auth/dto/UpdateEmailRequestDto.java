package com.paximum.paxassist.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// Body for PATCH /api/v1/auth/me — updates the authenticated user's login email.
// Field named "email" to match the frontend contract in frontend/src/api/authApi.ts
// (UpdateEmailRequest) and its MSW mock in mocks/handlers.ts.
public record UpdateEmailRequestDto(
        @NotBlank @Email @Size(max = 254) String email) {
}
