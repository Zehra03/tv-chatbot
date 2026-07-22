package com.paximum.paxassist.auth.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.paximum.paxassist.auth.dto.AuthResponseDto;
import com.paximum.paxassist.auth.dto.AuthUserDto;
import com.paximum.paxassist.auth.dto.LoginRequestDto;
import com.paximum.paxassist.auth.dto.RefreshRequestDto;
import com.paximum.paxassist.auth.dto.RegisterRequestDto;
import com.paximum.paxassist.auth.dto.ResetPasswordRequestDto;
import com.paximum.paxassist.auth.dto.UpdateEmailRequestDto;
import com.paximum.paxassist.auth.exception.EmailAlreadyExistsException;
import com.paximum.paxassist.auth.exception.EmailNotFoundException;
import com.paximum.paxassist.auth.exception.InvalidRefreshTokenException;
import com.paximum.paxassist.auth.service.AuthService;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    @Mock
    private AuthService authService;

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new AuthController(authService))
                .setControllerAdvice(new AuthExceptionHandler(), new com.paximum.paxassist.config.GlobalExceptionHandler())
                // logout uses @AuthenticationPrincipal; register the resolver so standalone MockMvc
                // supplies null (no auth) instead of trying to instantiate UserPrincipal as a model attr.
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .build();
    }

    @Test
    void register_returns201WithUserAndToken() throws Exception {
        RegisterRequestDto request = new RegisterRequestDto("new@example.com", "password123", "New User");
        AuthResponseDto response = new AuthResponseDto(
                new AuthUserDto("1", "new@example.com", "New User", "USER"), "jwt-token", "refresh-token");
        when(authService.register(any())).thenReturn(response);

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.token").value("jwt-token"))
                .andExpect(jsonPath("$.refreshToken").value("refresh-token"))
                .andExpect(jsonPath("$.user.email").value("new@example.com"));
    }

    @Test
    void register_returns409WhenEmailAlreadyExists() throws Exception {
        RegisterRequestDto request = new RegisterRequestDto("dup@example.com", "password123", null);
        when(authService.register(any())).thenThrow(new EmailAlreadyExistsException("dup@example.com"));

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("EMAIL_ALREADY_EXISTS"));
    }

    @Test
    void register_returns400WhenPasswordTooShort() throws Exception {
        RegisterRequestDto request = new RegisterRequestDto("new@example.com", "short", null);

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void login_returns200WithUserAndToken() throws Exception {
        LoginRequestDto request = new LoginRequestDto("user@example.com", "password123");
        AuthResponseDto response = new AuthResponseDto(
                new AuthUserDto("2", "user@example.com", null, "USER"), "jwt-token", "refresh-token");
        when(authService.login(any())).thenReturn(response);

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("jwt-token"))
                .andExpect(jsonPath("$.refreshToken").value("refresh-token"))
                .andExpect(jsonPath("$.user.id").value("2"));
    }

    @Test
    void login_returns401OnBadCredentials() throws Exception {
        LoginRequestDto request = new LoginRequestDto("user@example.com", "wrong-password");
        when(authService.login(any()))
                .thenThrow(new org.springframework.security.authentication.BadCredentialsException("Bad credentials"));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("INVALID_CREDENTIALS"));
    }

    @Test
    void refresh_returns200WithRotatedTokens() throws Exception {
        RefreshRequestDto request = new RefreshRequestDto("old-refresh");
        AuthResponseDto response = new AuthResponseDto(
                new AuthUserDto("2", "user@example.com", null, "USER"), "new-jwt", "new-refresh");
        when(authService.refresh(any())).thenReturn(response);

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("new-jwt"))
                .andExpect(jsonPath("$.refreshToken").value("new-refresh"));
    }

    @Test
    void refresh_returns400WhenTokenBlank() throws Exception {
        RefreshRequestDto request = new RefreshRequestDto("");

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void refresh_returns401WhenTokenInvalid() throws Exception {
        RefreshRequestDto request = new RefreshRequestDto("bad-refresh");
        when(authService.refresh(any())).thenThrow(new InvalidRefreshTokenException());

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("INVALID_REFRESH_TOKEN"));
    }

    @Test
    void logout_returns204() throws Exception {
        mockMvc.perform(post("/api/v1/auth/logout"))
                .andExpect(status().isNoContent());
    }

    @Test
    void resetPassword_returns200WithMessage() throws Exception {
        ResetPasswordRequestDto request = new ResetPasswordRequestDto("user@example.com", "new-password123");

        mockMvc.perform(post("/api/v1/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    void resetPassword_returns400WhenPasswordTooShort() throws Exception {
        ResetPasswordRequestDto request = new ResetPasswordRequestDto("user@example.com", "short");

        mockMvc.perform(post("/api/v1/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void resetPassword_returns404WhenEmailNotRegistered() throws Exception {
        ResetPasswordRequestDto request = new ResetPasswordRequestDto("ghost@example.com", "new-password123");
        doThrow(new EmailNotFoundException("ghost@example.com"))
                .when(authService).resetPassword(any(), any());

        mockMvc.perform(post("/api/v1/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("EMAIL_NOT_FOUND"));
    }

    @Test
    void updateEmail_returns400WhenEmailInvalid() throws Exception {
        // Validation fails before the handler body runs, so a null principal (standalone MockMvc)
        // never reached — this exercises the @Valid boundary on PATCH /me.
        UpdateEmailRequestDto request = new UpdateEmailRequestDto("not-an-email");

        mockMvc.perform(patch("/api/v1/auth/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }
}
