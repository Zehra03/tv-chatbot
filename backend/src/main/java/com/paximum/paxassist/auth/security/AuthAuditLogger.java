package com.paximum.paxassist.auth.security;

import org.springframework.stereotype.Component;

import com.paximum.paxassist.audit.AuditLogModule;

/**
 * Forwards authentication/authorization failures to the Log module without blocking the
 * request/response cycle. Mirrors the guard module's {@code GuardAuditLogger} pattern.
 */
@Component
public class AuthAuditLogger {

    private final AuditLogModule auditLogModule;

    public AuthAuditLogger(AuditLogModule auditLogModule) {
        this.auditLogModule = auditLogModule;
    }

    public void logAuthenticationFailureAsync(String requestUri, String reason) {
        auditLogModule.logSecurityEventAsync(
                "Authentication failed (401): uri=%s, reason=%s".formatted(sanitize(requestUri), sanitize(reason)));
    }

    public void logAccessDeniedAsync(String requestUri, String principal) {
        auditLogModule.logSecurityEventAsync(
                "Access denied (403): uri=%s, principal=%s".formatted(sanitize(requestUri), sanitize(principal)));
    }

    // Strips CR/LF from request-derived values so a crafted URI/header can't forge extra log lines.
    private static String sanitize(String value) {
        if (value == null) {
            return null;
        }
        return value.replaceAll("[\\r\\n]", "_");
    }
}
