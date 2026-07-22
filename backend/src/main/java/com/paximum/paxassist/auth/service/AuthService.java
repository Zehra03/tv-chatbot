package com.paximum.paxassist.auth.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
import com.paximum.paxassist.auth.exception.EmailNotFoundException;
import com.paximum.paxassist.auth.repository.UserRepository;
import com.paximum.paxassist.auth.security.JwtService;
import com.paximum.paxassist.auth.service.RefreshTokenService.RotatedTokens;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

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

    /**
     * Updates the authenticated user's login email. Idempotent when the new email matches the
     * current one (case-insensitive); otherwise enforces the same uniqueness rule as register and
     * throws {@link EmailAlreadyExistsException} on collision. Note: email is the JWT subject, so
     * the caller's current access token stops matching after a change — the frontend's silent
     * refresh (refresh token is keyed by user id, not email) transparently mints a new access token.
     */
    @Transactional
    public AuthUserDto updateEmail(Long userId, String newEmail) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalStateException("Authenticated user not found: " + userId));

        if (!user.getEmail().equalsIgnoreCase(newEmail)) {
            if (userRepository.existsByEmailIgnoreCase(newEmail)) {
                throw new EmailAlreadyExistsException(newEmail);
            }
            user.setEmail(newEmail);
            userRepository.save(user);
        }
        return toUserDto(user);
    }

    /**
     * Self-service password reset: sets a new password for the account with the given email,
     * with no email-link round-trip (SMTP is out of scope for this project). Unlike the previous
     * request-a-link flow, this changes the password directly, so it necessarily reveals whether
     * the email is registered ({@link EmailNotFoundException} → 404) — a deliberate convenience
     * trade for this demo. Every existing refresh token is revoked so any other open session is
     * invalidated by the change; the caller then logs in fresh with the new password.
     */
    @Transactional
    public void resetPassword(String email, String newPassword) {
        User user = userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new EmailNotFoundException(email));
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        userRepository.save(user);
        refreshTokenService.revokeAllForUser(user.getId());
        log.info("Self-service password reset completed for {}", email);
    }

    private AuthResponseDto issueTokens(User user) {
        String accessToken = jwtService.generateToken(user.getEmail(), user.getRole().name());
        String refreshToken = refreshTokenService.issue(user);
        return new AuthResponseDto(toUserDto(user), accessToken, refreshToken);
    }

    private static AuthUserDto toUserDto(User user) {
        return new AuthUserDto(String.valueOf(user.getId()), user.getEmail(), user.getDisplayName(),
                user.getRole().name());
    }
}
