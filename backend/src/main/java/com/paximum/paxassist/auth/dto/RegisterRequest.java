package com.paximum.paxassist.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(

        @NotBlank(message = "Ad soyad boş olamaz")
        @Schema(description = "Kullanıcının tam adı", example = "Ali Veli")
        String fullName,

        @NotBlank(message = "E-posta boş olamaz")
        @Email(message = "Geçerli bir e-posta adresi girin")
        @Schema(description = "E-posta adresi", example = "ali@example.com")
        String email,

        @NotBlank(message = "Şifre boş olamaz")
        @Size(min = 8, message = "Şifre en az 8 karakter olmalıdır")
        @Schema(description = "Şifre (en az 8 karakter)", example = "gizli1234")
        String password
) {}
