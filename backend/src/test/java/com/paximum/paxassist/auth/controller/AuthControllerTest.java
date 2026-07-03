package com.paximum.paxassist.auth.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.paximum.paxassist.auth.dto.AuthResponseDto;
import com.paximum.paxassist.auth.dto.AuthUserDto;
import com.paximum.paxassist.auth.dto.LoginRequestDto;
import com.paximum.paxassist.auth.dto.RegisterRequestDto;
import com.paximum.paxassist.auth.exception.EmailAlreadyExistsException;
import com.paximum.paxassist.auth.service.AuthService;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
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
                .build();
    }

    @Test
    void register_returns201WithUserAndToken() throws Exception {
        RegisterRequestDto request = new RegisterRequestDto("new@example.com", "password123", "New User");
        AuthResponseDto response = new AuthResponseDto(
                new AuthUserDto("1", "new@example.com", "New User"), "jwt-token");
        when(authService.register(any())).thenReturn(response);

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.token").value("jwt-token"))
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
                new AuthUserDto("2", "user@example.com", null), "jwt-token");
        when(authService.login(any())).thenReturn(response);

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("jwt-token"))
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
    void logout_returns204() throws Exception {
        mockMvc.perform(post("/api/v1/auth/logout"))
                .andExpect(status().isNoContent());
    }
}
