package com.paximum.paxassist.auth.security;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.paximum.paxassist.audit.AuditLogModule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AuthAuditLoggerTest {

    @Mock
    private AuditLogModule auditLogModule;

    @InjectMocks
    private AuthAuditLogger authAuditLogger;

    @Test
    void logAuthenticationFailure_recordsUriAndReasonAsAStructuredEvent() {
        authAuditLogger.logAuthenticationFailure("/api/v1/reservations", "Full authentication is required");

        // action/status are fields, not prose: a 401 must be countable without parsing a sentence.
        verify(auditLogModule, times(1)).logSecurityEvent(
                eq("authenticationFailure"), eq("UNAUTHORIZED"),
                contains("/api/v1/reservations"), contains("Full authentication is required"));
    }

    @Test
    void logAccessDenied_recordsUriAndPrincipalAsAStructuredEvent() {
        authAuditLogger.logAccessDenied("/api/v1/reservations", "user@example.com");

        verify(auditLogModule, times(1)).logSecurityEvent(
                eq("accessDenied"), eq("FORBIDDEN"),
                contains("/api/v1/reservations"), contains("user@example.com"));
    }

    @Test
    void sanitizesCrlfInLoggedValues() {
        // Without this a crafted URI could forge extra log lines — and now that lines are JSON, a
        // forged line would parse as a real event rather than read as obvious garbage.
        authAuditLogger.logAuthenticationFailure(
                "/api/v1/reservations\nFAKE LOG LINE: admin logged in", "reason\r\ninjected");

        ArgumentCaptor<String> subject = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> message = ArgumentCaptor.forClass(String.class);
        verify(auditLogModule, times(1)).logSecurityEvent(
                anyString(), anyString(), subject.capture(), message.capture());
        assertThat(subject.getValue()).doesNotContain("\n").doesNotContain("\r");
        assertThat(message.getValue()).doesNotContain("\n").doesNotContain("\r");
    }
}
