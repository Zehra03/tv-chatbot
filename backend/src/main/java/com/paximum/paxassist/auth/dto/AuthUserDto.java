package com.paximum.paxassist.auth.dto;

// Matches frontend/src/api/authApi.ts's AuthUser interface: { id, email, name?, role }.
//
// `role` ("USER" / "ADMIN") is what lets the frontend decide whether to render the admin panel
// at all. It is a CONVENIENCE, not the security boundary: SecurityConfig already gates
// /api/v1/admin/** with hasRole("ADMIN") server-side, so a client that forges this field gains
// nothing but a screen that 403s on every request.
public record AuthUserDto(String id, String email, String name, String role) {
}
