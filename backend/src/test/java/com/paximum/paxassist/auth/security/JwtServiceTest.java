package com.paximum.paxassist.auth.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class JwtServiceTest {

    private JwtService jwtService;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService(
                "unit-test-secret-key-please-be-32-bytes-minimum",
                60,
                "paxassist-test");
    }

    @Test
    void generateToken_thenExtractEmail_roundTrips() {
        String token = jwtService.generateToken("user@example.com", "USER");

        assertThat(jwtService.extractEmail(token)).isEqualTo("user@example.com");
    }

    @Test
    void isTokenValid_returnsTrue_forMatchingEmailAndUnexpiredToken() {
        String token = jwtService.generateToken("user@example.com", "USER");

        assertThat(jwtService.isTokenValid(token, "user@example.com")).isTrue();
        assertThat(jwtService.isTokenValid(token, "USER@EXAMPLE.COM")).isTrue();
    }

    @Test
    void isTokenValid_returnsFalse_forMismatchedEmail() {
        String token = jwtService.generateToken("user@example.com", "USER");

        assertThat(jwtService.isTokenValid(token, "someone-else@example.com")).isFalse();
    }

    @Test
    void isTokenValid_returnsFalse_forExpiredToken() {
        JwtService shortLivedJwtService = new JwtService(
                "unit-test-secret-key-please-be-32-bytes-minimum", 0, "paxassist-test");
        String token = shortLivedJwtService.generateToken("user@example.com", "USER");

        assertThat(shortLivedJwtService.isTokenValid(token, "user@example.com")).isFalse();
    }

    @Test
    void isTokenValid_returnsFalse_forMalformedToken() {
        assertThat(jwtService.isTokenValid("not-a-jwt", "user@example.com")).isFalse();
    }

    @Test
    void isTokenValid_returnsFalse_forTokenSignedWithDifferentSecret() {
        JwtService otherJwtService = new JwtService(
                "a-completely-different-secret-key-of-32-bytes+", 60, "paxassist-test");
        String token = otherJwtService.generateToken("user@example.com", "USER");

        assertThat(jwtService.isTokenValid(token, "user@example.com")).isFalse();
    }
}
