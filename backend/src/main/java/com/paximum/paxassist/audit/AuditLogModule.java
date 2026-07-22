package com.paximum.paxassist.audit;

/**
 * Port for writing security-relevant audit events (guard blocks, authentication failures, access
 * denials).
 *
 * <p>The parameters mirror the activity log's shape on purpose: a security event is the kind of
 * event you most want to filter and count, so it must arrive as <em>fields</em>, not as a sentence
 * with the interesting parts glued into the middle of it.
 *
 * <p>Implementations must never receive raw user input — callers mask and sanitize first (see
 * {@code GuardAuditLogger}).
 */
public interface AuditLogModule {

    /**
     * @param action  what happened, e.g. {@code "guardBlock"} / {@code "authenticationFailure"}
     * @param status  outcome label, e.g. {@code "BLOCKED"} / {@code "DENIED"}
     * @param subject PII-safe context the event is about (a masked input, a request URI)
     * @param message human-readable detail
     */
    void logSecurityEvent(String action, String status, String subject, String message);
}
