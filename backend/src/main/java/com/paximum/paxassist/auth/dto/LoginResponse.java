package com.paximum.paxassist.auth.dto;

import com.paximum.paxassist.auth.domain.User;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Başarılı giriş sonrası dönen JWT token ve kullanıcı bilgileri")
public record LoginResponse(

        @Schema(description = "JWT erişim token'ı") String token,
        @Schema(description = "Kullanıcı ID") Long userId,
        @Schema(description = "E-posta adresi") String email,
        @Schema(description = "Görünen ad") String displayName,
        @Schema(description = "Rol") String role
) {
    public static LoginResponse of(String token, User user) {
        return new LoginResponse(
                token,
                user.getId(),
                user.getEmail(),
                user.getDisplayName(),
                user.getRole()
        );
    }
}
