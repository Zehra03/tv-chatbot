package com.paximum.paxassist.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record LoginRequest(

        @NotBlank(message = "E-posta boş olamaz")
        @Email(message = "Geçerli bir e-posta adresi girin")
        @Schema(description = "Kayıtlı e-posta adresi", example = "ali@example.com")
        String email,

        @NotBlank(message = "Şifre boş olamaz")
        @Schema(description = "Kullanıcı şifresi", example = "gizli1234")
        String password
) {}
