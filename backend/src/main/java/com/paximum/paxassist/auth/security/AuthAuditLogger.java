package com.paximum.paxassist.auth.security;

import org.springframework.stereotype.Component;

import com.paximum.paxassist.audit.AuditLogModule;

/**
 * Forwards authentication/authorization failures to the audit sink as structured security events.
 * Mirrors the guard module's {@code GuardAuditLogger} pattern.
 */
@Component
public class AuthAuditLogger {

    private final AuditLogModule auditLogModule;

    public AuthAuditLogger(AuditLogModule auditLogModule) {
        this.auditLogModule = auditLogModule;
    }

    public void logAuthenticationFailure(String requestUri, String reason) {
        auditLogModule.logSecurityEvent("authenticationFailure", "UNAUTHORIZED",
                "uri=" + sanitize(requestUri),
                "Authentication failed (401): reason=" + sanitize(reason));
    }

    public void logAccessDenied(String requestUri, String principal) {
        auditLogModule.logSecurityEvent("accessDenied", "FORBIDDEN",
                "uri=" + sanitize(requestUri),
                "Access denied (403): principal=" + sanitize(principal));
    }

    // Strips CR/LF from request-derived values so a crafted URI/header can't forge extra log lines.
    private static String sanitize(String value) {
        if (value == null) {
            return null;
        }
        return value.replaceAll("[\\r\\n]", "_");
    }
}
