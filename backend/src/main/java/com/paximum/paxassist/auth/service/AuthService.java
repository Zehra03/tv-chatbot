package com.paximum.paxassist.auth.service;

import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.paximum.paxassist.auth.domain.Role;
import com.paximum.paxassist.auth.domain.User;
import com.paximum.paxassist.auth.dto.AuthResponseDto;
import com.paximum.paxassist.auth.dto.AuthUserDto;
import com.paximum.paxassist.auth.dto.LoginRequestDto;
import com.paximum.paxassist.auth.dto.RefreshRequestDto;
import com.paximum.paxassist.auth.dto.RegisterRequestDto;
import com.paximum.paxassist.auth.exception.EmailAlreadyExistsException;
import com.paximum.paxassist.auth.repository.UserRepository;
import com.paximum.paxassist.auth.security.JwtService;
import com.paximum.paxassist.auth.service.RefreshTokenService.RotatedTokens;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;

    public AuthService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            AuthenticationManager authenticationManager,
            JwtService jwtService,
            RefreshTokenService refreshTokenService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
        this.refreshTokenService = refreshTokenService;
    }

    @Transactional
    public AuthResponseDto register(RegisterRequestDto request) {
        if (userRepository.existsByEmailIgnoreCase(request.email())) {
            throw new EmailAlreadyExistsException(request.email());
        }

        User user = User.builder()
                .email(request.email())
                .passwordHash(passwordEncoder.encode(request.password()))
                .displayName(request.name())
                .role(Role.USER)
                .build();
        userRepository.save(user);

        return issueTokens(user);
    }

    @Transactional
    public AuthResponseDto login(LoginRequestDto request) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.email(), request.password()));

        User user = userRepository.findByEmailIgnoreCase(request.email())
                .orElseThrow(() -> new IllegalStateException(
                        "Authenticated user not found: " + request.email()));

        return issueTokens(user);
    }

    /**
     * Exchanges a valid refresh token for a fresh access token, rotating the refresh token in the
     * process. Throws {@link com.paximum.paxassist.auth.exception.InvalidRefreshTokenException} if
     * the presented token is unknown, revoked, or expired.
     */
    @Transactional
    public AuthResponseDto refresh(RefreshRequestDto request) {
        RotatedTokens rotated = refreshTokenService.rotate(request.refreshToken());
        User user = rotated.user();
        String accessToken = jwtService.generateToken(user.getEmail(), user.getRole().name());
        return new AuthResponseDto(toUserDto(user), accessToken, rotated.refreshToken());
    }

    /** Revokes every refresh token for the user so logout takes effect server-side. */
    @Transactional
    public void logout(Long userId) {
        refreshTokenService.revokeAllForUser(userId);
    }

    private AuthResponseDto issueTokens(User user) {
        String accessToken = jwtService.generateToken(user.getEmail(), user.getRole().name());
        String refreshToken = refreshTokenService.issue(user);
        return new AuthResponseDto(toUserDto(user), accessToken, refreshToken);
    }

    private static AuthUserDto toUserDto(User user) {
        return new AuthUserDto(String.valueOf(user.getId()), user.getEmail(), user.getDisplayName());
    }
}
