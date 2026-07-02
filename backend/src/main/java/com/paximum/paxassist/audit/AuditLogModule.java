package com.paximum.paxassist.audit;

/**
 * Port for writing security-relevant audit events. Implementations are expected to persist
 * or forward {@code message} without blocking the caller.
 */
public interface AuditLogModule {

    void logSecurityEventAsync(String message);
}
