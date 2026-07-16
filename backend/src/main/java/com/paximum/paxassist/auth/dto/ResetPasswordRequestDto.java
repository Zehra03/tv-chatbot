package com.paximum.paxassist.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// Body for POST /api/v1/auth/reset-password — self-service password change with no email link
// (see AuthService#resetPassword). Field names match frontend/src/api/authApi.ts (resetPassword)
// and the MSW mock in mocks/handlers.ts. The password rule mirrors RegisterRequestDto so a reset
// can't set a weaker password than registration allows.
public record ResetPasswordRequestDto(
        @NotBlank @Email @Size(max = 254) String email,
        @NotBlank @Size(min = 8, max = 72) String password) {
}
