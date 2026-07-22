package com.paximum.paxassist.guard;

import org.springframework.stereotype.Component;

import com.paximum.paxassist.audit.AuditLogModule;

/**
 * Masks KVKK-sensitive data (credit card, TCKN, IBAN) out of blocked-request input before it
 * ever reaches a log sink; non-PII block reasons (prompt injection, profanity) are logged as-is.
 */
@Component
public class GuardAuditLogger {

    private final AuditLogModule auditLogModule;

    public GuardAuditLogger(AuditLogModule auditLogModule) {
        this.auditLogModule = auditLogModule;
    }

    public void logBlockedRequest(String originalInput, String reason) {
        String maskedInput = SensitiveDataMasker.mask(originalInput, reason);
        auditLogModule.logSecurityEvent("guardBlock", "BLOCKED",
                sanitize(maskedInput),
                "Blocked request: reason=" + sanitize(reason));
    }

    // Strips CR/LF from user-derived values before they reach the log sink, so a malicious
    // input can't forge extra log lines (log injection / CRLF injection into the audit log).
    private static String sanitize(String value) {
        if (value == null) {
            return null;
        }
        return value.replaceAll("[\\r\\n]", "_");
    }
}
