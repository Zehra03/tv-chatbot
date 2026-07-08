package com.paximum.paxassist.auth.dto;

// Matches frontend/src/api/authApi.ts's AuthResponse interface: { user, token, refreshToken }.
// `token` is the short-lived access JWT; `refreshToken` is the long-lived opaque rotating token
// the client posts to /auth/refresh to mint a new access token. Both are opaque to the frontend,
// so no token-metadata fields are exposed here.
public record AuthResponseDto(AuthUserDto user, String token, String refreshToken) {
}
