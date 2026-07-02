package com.paximum.paxassist.auth.controller;

import com.paximum.paxassist.auth.dto.LoginRequest;
import com.paximum.paxassist.auth.dto.LoginResponse;
import com.paximum.paxassist.auth.dto.RegisterRequest;
import com.paximum.paxassist.auth.dto.UserResponse;
import com.paximum.paxassist.auth.exception.InvalidCredentialsException;
import com.paximum.paxassist.auth.service.AuthService;
import com.paximum.paxassist.chat.dto.ErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@Tag(name = "Auth", description = "Kimlik doğrulama işlemleri")
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @Operation(summary = "Yeni kullanıcı kaydı",
               description = "E-posta ve şifre ile yeni bir kullanıcı oluşturur.")
    @ApiResponse(responseCode = "201", description = "Kullanıcı başarıyla oluşturuldu",
            content = @Content(schema = @Schema(implementation = UserResponse.class)))
    @ApiResponse(responseCode = "400", description = "Validasyon hatası",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "409", description = "E-posta zaten kayıtlı",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponse register(@RequestBody @Valid RegisterRequest request) {
        return authService.register(request);
    }

    @Operation(summary = "Kullanıcı girişi",
               description = "E-posta ve şifre doğrulanır, 24 saatlik JWT token döner.")
    @ApiResponse(responseCode = "200", description = "Giriş başarılı",
            content = @Content(schema = @Schema(implementation = LoginResponse.class)))
    @ApiResponse(responseCode = "400", description = "Validasyon hatası",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "401", description = "E-posta veya şifre hatalı",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @PostMapping("/login")
    public LoginResponse login(@RequestBody @Valid LoginRequest request) {
        return authService.login(request);
    }

    @Operation(summary = "Çıkış",
               description = "JWT stateless olduğundan sunucu tarafında işlem yapılmaz. " +
                             "İstemci tarafında token silinmelidir.")
    @ApiResponse(responseCode = "200", description = "Çıkış başarılı")
    @PostMapping("/logout")
    public Map<String, String> logout() {
        return Map.of("message", "Çıkış başarılı");
    }

    @Operation(summary = "Mevcut kullanıcı bilgileri",
               description = "Authorization: Bearer <token> header'ından kimlik doğrulanır, " +
                             "kullanıcı bilgileri döner.")
    @ApiResponse(responseCode = "200", description = "Kullanıcı bilgileri",
            content = @Content(schema = @Schema(implementation = UserResponse.class)))
    @ApiResponse(responseCode = "401", description = "Token geçersiz veya eksik",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "404", description = "Kullanıcı bulunamadı",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @GetMapping("/me")
    public UserResponse me() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof Long userId)) {
            throw new InvalidCredentialsException();
        }
        return authService.me(userId);
    }
}
