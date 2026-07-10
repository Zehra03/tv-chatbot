package com.paximum.paxassist.auth.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.paximum.paxassist.auth.dto.AuthResponseDto;
import com.paximum.paxassist.auth.dto.AuthUserDto;
import com.paximum.paxassist.auth.dto.LoginRequestDto;
import com.paximum.paxassist.auth.dto.RefreshRequestDto;
import com.paximum.paxassist.auth.dto.RegisterRequestDto;
import com.paximum.paxassist.auth.security.UserPrincipal;
import com.paximum.paxassist.auth.service.AuthService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    public ResponseEntity<AuthResponseDto> register(@Valid @RequestBody RegisterRequestDto request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.register(request));
    }

    @PostMapping("/login")
    public AuthResponseDto login(@Valid @RequestBody LoginRequestDto request) {
        return authService.login(request);
    }

    // Rotates the presented refresh token for a new access + refresh token pair. Public (like
    // login) because the access token may already be expired when this is called; the refresh
    // token itself is the credential.
    @PostMapping("/refresh")
    public AuthResponseDto refresh(@Valid @RequestBody RefreshRequestDto request) {
        return authService.refresh(request);
    }

    // Revokes the caller's refresh tokens server-side, then the frontend discards its access token.
    // principal is null only in standalone/unauthenticated setups; a real request always carries it.
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@AuthenticationPrincipal UserPrincipal principal) {
        if (principal != null) {
            authService.logout(principal.getId());
        }
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me")
    public AuthUserDto me(@AuthenticationPrincipal UserPrincipal principal) {
        return new AuthUserDto(String.valueOf(principal.getId()), principal.getUsername(), principal.getDisplayName());
    }
}
