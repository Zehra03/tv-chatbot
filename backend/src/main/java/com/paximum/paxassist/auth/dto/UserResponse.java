package com.paximum.paxassist.auth.dto;

import com.paximum.paxassist.auth.domain.User;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

@Schema(description = "Kayıtlı kullanıcı bilgileri")
public record UserResponse(

        @Schema(description = "Kullanıcı ID") Long id,
        @Schema(description = "E-posta adresi") String email,
        @Schema(description = "Görünen ad") String displayName,
        @Schema(description = "Rol") String role,
        @Schema(description = "Kayıt tarihi") Instant createdAt
) {
    public static UserResponse from(User user) {
        return new UserResponse(
                user.getId(),
                user.getEmail(),
                user.getDisplayName(),
                user.getRole(),
                user.getCreatedAt()
        );
    }
}
