package com.paximum.paxassist.auth.dto;

// Matches frontend/src/api/authApi.ts's AuthResponse interface: { user, token }.
// The token is treated as opaque by the frontend, so no token-metadata fields are exposed here.
public record AuthResponseDto(AuthUserDto user, String token) {
}
