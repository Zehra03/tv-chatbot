package com.paximum.paxassist.audit;

import org.springframework.stereotype.Component;

import com.paximum.paxassist.common.log.ActivityLog;

/**
 * Writes security events to the same structured stream as every other activity event, under the
 * {@code SecurityModule} name — so a guard block or a 401 is filterable by
 * {@code activity.module}/{@code activity.status} exactly like a search or a booking, and carries
 * the request's {@code requestId}/{@code userId}/{@code guestId} correlation fields.
 *
 * <p>Previously these went to a separate {@code SECURITY_AUDIT} logger as one flat sentence, which
 * made the events you most want to count and filter the only ones you could not query.
 *
 * <p><b>Why it is no longer run off-thread.</b> The old implementation hopped to a
 * {@code CompletableFuture}, reasoning that a guard check must not be slowed down by logging. That
 * hop leaves the request thread — and the MDC lives on the request thread, so every security event
 * lost its correlation fields: the record said "someone was blocked" with no way to tell who, or in
 * which request. Writing one line to stdout costs far less than that loss. If console contention
 * ever shows up under load, the answer is an async <em>appender</em> (which propagates the MDC), not
 * a thread hop in application code.
 */
@Component
public class Slf4jAuditLogModule implements AuditLogModule {

    private static final String MODULE = "SecurityModule";

    private final ActivityLog activityLog;

    public Slf4jAuditLogModule(ActivityLog activityLog) {
        this.activityLog = activityLog;
    }

    @Override
    public void logSecurityEvent(String action, String status, String subject, String message) {
        activityLog.logActivity(MODULE, action, subject, status, message);
    }
}
