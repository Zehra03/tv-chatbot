package com.paximum.paxassist.admin.dto;

import java.time.Instant;

public record UserAdminDto(
        Long id,
        String email,
        String displayName,
        String role,
        Instant createdAt
) {}
