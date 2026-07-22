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
 * <p><b>PII:</b> callers pass a masked summary (see {@code ReservationService#maskedSummary}) —
 * passenger names and contact details must never reach this method. That remains the contract, but
 * it is no longer only a contract: every value goes through {@link LogSafeText}, which strips CR/LF
 * and masks card/national-id/IBAN shapes, so a careless caller leaks nothing and cannot forge a log
 * line.
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
        String safeRequest = LogSafeText.scrub(requestData);
        if (safeRequest != null) {
            MDC.put(REQUEST, safeRequest);
        }
        String safeMessage = LogSafeText.scrub(message);
        try {
            // Level follows the outcome, so alerting can key on level without parsing status text.
            //
            // FAILED/FAILURE is WARN rather than ERROR on purpose: most of them are user-level
            // outcomes, not server faults — an expired preview, an ownership mismatch, a duplicate
            // confirm. A genuine fault throws, and the exception is logged at ERROR with its stack
            // trace by the global handler, so real errors are still ERROR exactly once.
            switch (levelFor(status)) {
                case WARN -> log.warn(safeMessage);
                default -> log.info(safeMessage);
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

    private Level levelFor(String status) {
        if (status == null) {
            return Level.INFO;
        }
        return status.startsWith("FAIL") || "ERROR".equals(status) || "WARNING".equals(status)
                ? Level.WARN
                : Level.INFO;
    }

    private enum Level { INFO, WARN }
}
