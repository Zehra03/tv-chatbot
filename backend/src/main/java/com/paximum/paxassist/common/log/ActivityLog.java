package com.paximum.paxassist.common.log;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

/**
 * Business activity events (a hotel search ran, a reservation was confirmed) written to the
 * application's own log stream, with {@code module} / {@code action} / {@code status} carried as
 * structured fields rather than glued into the message text.
 *
 * <p>Replaces {@code LogModuleClient}, which POSTed each event to a separate log service that was
 * never built — every call failed and was swallowed, so these events had no destination at all.
 * They now go where the rest of the application's logs go: stdout. Under the {@code prod} profile
 * that stream is JSON (see {@code application-prod.yml}), so the fields below come out as queryable
 * attributes; in dev the same event stays readable as plain text.
 *
 * <p><b>Never database-backed.</b> Logs are deliberately not persisted to Postgres — the
 * {@code logging.app_logs} table that once existed for this was dropped in V8.
 *
 * <p><b>Not async, on purpose.</b> The old client ran {@code @Async} because it made a network call;
 * writing a line to stdout does not need a thread hop, and adding one would cost more than it saves
 * while scrambling the order of events relative to the surrounding logs.
 *
 * <p><b>PII:</b> {@code requestData} is written verbatim. Callers pass a masked summary (see
 * {@code ReservationService#maskedSummary}); passenger names and contact details must never reach
 * this method.
 */
@Component
public class ActivityLog {

    private static final Logger log = LoggerFactory.getLogger("ACTIVITY");

    private static final String MODULE = "activity.module";
    private static final String ACTION = "activity.action";
    private static final String STATUS = "activity.status";
    private static final String REQUEST = "activity.request";

    /**
     * Records one business activity event.
     *
     * @param module      owning module, e.g. {@code "ReservationModule"}
     * @param action      the operation, e.g. {@code "confirmReservation"}
     * @param requestData PII-safe summary of the input (see the class note on PII)
     * @param status      outcome, e.g. {@code SUCCESS} / {@code FAILED} / {@code WARNING}
     * @param message     human-readable detail
     */
    public void logActivity(String module, String action, String requestData, String status, String message) {
        MDC.put(MODULE, module);
        MDC.put(ACTION, action);
        MDC.put(STATUS, status);
        if (requestData != null) {
            MDC.put(REQUEST, requestData);
        }
        try {
            // A failed activity is a WARN, not an ERROR: the call sites that hit a genuine fault
            // already log it at ERROR with the stack trace. A second ERROR line for the same event
            // would double-count in any alerting built on error rate.
            if (isFailure(status)) {
                log.warn(message);
            } else {
                log.info(message);
            }
        } finally {
            // Threads are pooled and reused. Without this, these fields would stick to every
            // unrelated log line the same worker writes afterwards — the event would look like it
            // happened over and over.
            MDC.remove(MODULE);
            MDC.remove(ACTION);
            MDC.remove(STATUS);
            MDC.remove(REQUEST);
        }
    }

    private boolean isFailure(String status) {
        return status != null
                && (status.startsWith("FAIL") || "ERROR".equals(status));
    }
}
