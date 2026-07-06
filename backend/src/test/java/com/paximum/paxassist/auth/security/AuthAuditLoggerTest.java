package com.paximum.paxassist.auth.security;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.paximum.paxassist.audit.AuditLogModule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AuthAuditLoggerTest {

    @Mock
    private AuditLogModule auditLogModule;

    @InjectMocks
    private AuthAuditLogger authAuditLogger;

    @Test
    void logAuthenticationFailureAsync_includesUriAndReason() {
        authAuditLogger.logAuthenticationFailureAsync("/api/v1/reservations", "Full authentication is required");

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(auditLogModule, times(1)).logSecurityEventAsync(captor.capture());
        assertThat(captor.getValue())
                .contains("/api/v1/reservations")
                .contains("Full authentication is required");
    }

    @Test
    void logAccessDeniedAsync_includesUriAndPrincipal() {
        authAuditLogger.logAccessDeniedAsync("/api/v1/reservations", "user@example.com");

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(auditLogModule, times(1)).logSecurityEventAsync(captor.capture());
        assertThat(captor.getValue())
                .contains("/api/v1/reservations")
                .contains("user@example.com");
    }

    @Test
    void sanitizesCrlfInLoggedValues() {
        authAuditLogger.logAuthenticationFailureAsync(
                "/api/v1/reservations\nFAKE LOG LINE: admin logged in", "reason\r\ninjected");

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(auditLogModule, times(1)).logSecurityEventAsync(captor.capture());
        assertThat(captor.getValue()).doesNotContain("\n").doesNotContain("\r");
    }
}
