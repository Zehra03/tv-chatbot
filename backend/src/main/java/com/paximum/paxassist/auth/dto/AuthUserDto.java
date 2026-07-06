package com.paximum.paxassist.auth.dto;

// Matches frontend/src/api/authApi.ts's AuthUser interface: { id, email, name? }.
public record AuthUserDto(String id, String email, String name) {
}
