package com.paximum.paxassist.auth.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.HexFormat;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.paximum.paxassist.auth.domain.RefreshToken;
import com.paximum.paxassist.auth.domain.User;
import com.paximum.paxassist.auth.exception.InvalidRefreshTokenException;
import com.paximum.paxassist.auth.repository.RefreshTokenRepository;

/**
 * Owns the lifecycle of the persisted, rotating refresh tokens. The raw token handed to the client
 * is a 256-bit random string that is never stored; only its SHA-256 hash lives in the DB, so the
 * table cannot be used to replay sessions if it leaks. Every successful {@link #rotate} revokes the
 * presented token and issues a fresh one, which both limits a stolen token's lifetime and lets a
 * replay of an already-rotated token be detected (it is no longer active).
 */
@Service
public class RefreshTokenService {

    private final RefreshTokenRepository refreshTokenRepository;
    private final long expirationDays;
    private final SecureRandom secureRandom = new SecureRandom();
    private final Base64.Encoder tokenEncoder = Base64.getUrlEncoder().withoutPadding();

    public RefreshTokenService(
            RefreshTokenRepository refreshTokenRepository,
            @Value("${app.auth.refresh-token.expiration-days}") long expirationDays) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.expirationDays = expirationDays;
    }

    /** The user + newly minted raw refresh token produced by a rotation. */
    public record RotatedTokens(User user, String refreshToken) {
    }

    /** Issues a brand-new refresh token for the user and returns the raw (unhashed) value. */
    @Transactional
    public String issue(User user) {
        String rawToken = generateRawToken();
        RefreshToken entity = RefreshToken.builder()
                .user(user)
                .tokenHash(hash(rawToken))
                .expiresAt(Instant.now().plus(expirationDays, ChronoUnit.DAYS))
                .build();
        refreshTokenRepository.save(entity);
        return rawToken;
    }

    /**
     * Validates the presented raw token, revokes it, and issues a replacement (rotation). Throws
     * {@link InvalidRefreshTokenException} if the token is unknown, already revoked/rotated, or
     * expired.
     */
    @Transactional
    public RotatedTokens rotate(String rawToken) {
        RefreshToken current = refreshTokenRepository.findByTokenHash(hash(rawToken))
                .orElseThrow(InvalidRefreshTokenException::new);
        if (!current.isActive(Instant.now())) {
            throw new InvalidRefreshTokenException();
        }

        current.setRevokedAt(Instant.now());
        User user = current.getUser();
        String newRawToken = issue(user);
        return new RotatedTokens(user, newRawToken);
    }

    /** Revokes every active refresh token for the user — logout kills the session server-side. */
    @Transactional
    public void revokeAllForUser(Long userId) {
        refreshTokenRepository.revokeAllActiveForUser(userId, Instant.now());
    }

    private String generateRawToken() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return tokenEncoder.encodeToString(bytes);
    }

    private String hash(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashed);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is mandated by the JLS for every JVM; this cannot happen at runtime.
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
