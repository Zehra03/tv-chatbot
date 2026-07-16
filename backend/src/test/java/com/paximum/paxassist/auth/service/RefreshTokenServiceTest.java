package com.paximum.paxassist.auth.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.paximum.paxassist.auth.domain.RefreshToken;
import com.paximum.paxassist.auth.domain.Role;
import com.paximum.paxassist.auth.domain.User;
import com.paximum.paxassist.auth.exception.InvalidRefreshTokenException;
import com.paximum.paxassist.auth.repository.RefreshTokenRepository;
import com.paximum.paxassist.auth.service.RefreshTokenService.RotatedTokens;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RefreshTokenServiceTest {

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    private RefreshTokenService service;
    private final User user = User.builder()
            .id(1L)
            .email("user@example.com")
            .passwordHash("hashed")
            .role(Role.USER)
            .build();

    @BeforeEach
    void setUp() {
        service = new RefreshTokenService(refreshTokenRepository, 30);
    }

    @Test
    void issue_persistsHashOfTokenAndReturnsRawValue() {
        when(refreshTokenRepository.save(any(RefreshToken.class))).thenAnswer(inv -> inv.getArgument(0));

        String rawToken = service.issue(user);

        ArgumentCaptor<RefreshToken> captor = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokenRepository).save(captor.capture());
        RefreshToken saved = captor.getValue();

        assertThat(rawToken).isNotBlank();
        // The stored value is the SHA-256 hash, never the raw token itself.
        assertThat(saved.getTokenHash()).isEqualTo(sha256Hex(rawToken)).isNotEqualTo(rawToken);
        assertThat(saved.getUser()).isSameAs(user);
        assertThat(saved.getExpiresAt()).isAfter(Instant.now());
        assertThat(saved.getRevokedAt()).isNull();
    }

    @Test
    void rotate_revokesOldTokenAndIssuesNewOne() {
        RefreshToken existing = activeToken();
        when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(existing));
        when(refreshTokenRepository.save(any(RefreshToken.class))).thenAnswer(inv -> inv.getArgument(0));

        RotatedTokens result = service.rotate("old-raw-token");

        assertThat(existing.getRevokedAt()).isNotNull(); // old token rotated out
        assertThat(result.user()).isSameAs(user);
        assertThat(result.refreshToken()).isNotBlank().isNotEqualTo("old-raw-token");
        verify(refreshTokenRepository).save(any(RefreshToken.class)); // replacement persisted
    }

    @Test
    void rotate_throwsWhenTokenUnknown() {
        when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.rotate("missing"))
                .isInstanceOf(InvalidRefreshTokenException.class);

        verify(refreshTokenRepository, never()).save(any());
    }

    @Test
    void rotate_throwsWhenTokenAlreadyRevoked() {
        RefreshToken revoked = activeToken();
        revoked.setRevokedAt(Instant.now().minus(1, ChronoUnit.MINUTES));
        when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(revoked));

        assertThatThrownBy(() -> service.rotate("revoked-token"))
                .isInstanceOf(InvalidRefreshTokenException.class);

        verify(refreshTokenRepository, never()).save(any());
    }

    @Test
    void rotate_throwsWhenTokenExpired() {
        RefreshToken expired = activeToken();
        expired.setExpiresAt(Instant.now().minus(1, ChronoUnit.DAYS));
        when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(expired));

        assertThatThrownBy(() -> service.rotate("expired-token"))
                .isInstanceOf(InvalidRefreshTokenException.class);

        verify(refreshTokenRepository, never()).save(any());
    }

    @Test
    void revokeAllForUser_delegatesToRepository() {
        service.revokeAllForUser(5L);

        verify(refreshTokenRepository).revokeAllActiveForUser(eq(5L), any(Instant.class));
    }

    private RefreshToken activeToken() {
        return RefreshToken.builder()
                .user(user)
                .tokenHash("stub-hash")
                .expiresAt(Instant.now().plus(1, ChronoUnit.DAYS))
                .build();
    }

    private static String sha256Hex(String value) {
        try {
            byte[] hashed = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashed);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
