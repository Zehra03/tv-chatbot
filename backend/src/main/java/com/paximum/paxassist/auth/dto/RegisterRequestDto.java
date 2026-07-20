package com.paximum.paxassist.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

// Field is named "name" (not "displayName") to match the frontend contract in
// frontend/src/api/authApi.ts (RegisterRequest) and its MSW mock in mocks/handlers.ts.
public record RegisterRequestDto(
        @NotBlank @Email @Size(max = 254) String email,
        @NotBlank @Size(min = 8, max = 72) String password,
        @Pattern(regexp = "^[^0-9]*$", message = "İsim alanı sayı içeremez") @Size(max = 150) String name) {
}
