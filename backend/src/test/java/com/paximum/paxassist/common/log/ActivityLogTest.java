package com.paximum.paxassist.common.log;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

/**
 * Activity events must arrive as structured fields on the log line — that is the whole point of
 * option B (JSON to stdout) over the old fire-and-forget HTTP client.
 */
class ActivityLogTest {

    private ActivityLog activityLog;
    private Logger activityLogger;
    private ListAppender<ILoggingEvent> appender;

    @BeforeEach
    void setUp() {
        activityLog = new ActivityLog();
        activityLogger = (Logger) LoggerFactory.getLogger("ACTIVITY");
        appender = new ListAppender<>();
        appender.start();
        activityLogger.addAppender(appender);
    }

    @AfterEach
    void tearDown() {
        activityLogger.detachAppender(appender);
    }

    @Test
    void logActivity_putsModuleActionAndStatusOnTheEventAsFields() {
        activityLog.logActivity("ReservationModule", "confirmReservation", "products=H amount=1500 EUR",
                "SUCCESS", "Reservation PAX-20260722-ABC123 confirmed");

        ILoggingEvent event = onlyEvent();
        assertThat(event.getLevel()).isEqualTo(Level.INFO);
        assertThat(event.getFormattedMessage()).isEqualTo("Reservation PAX-20260722-ABC123 confirmed");
        // Fields, not message text: this is what the ECS formatter turns into queryable attributes.
        assertThat(event.getMDCPropertyMap())
                .containsEntry("activity.module", "ReservationModule")
                .containsEntry("activity.action", "confirmReservation")
                .containsEntry("activity.status", "SUCCESS")
                .containsEntry("activity.request", "products=H amount=1500 EUR");
    }

    @Test
    void logActivity_failedStatus_isWarnNotError() {
        // The call sites that hit a real fault already log it at ERROR with a stack trace; a second
        // ERROR for the same event would double-count in any alerting built on error rate.
        activityLog.logActivity("ReservationModule", "confirmReservation", "products=H",
                "FAILED", "Confirm failed: TourVisioUnavailable");

        assertThat(onlyEvent().getLevel()).isEqualTo(Level.WARN);
    }

    @Test
    void logActivity_clearsTheFieldsAfterwards_soTheyDoNotStickToLaterLines() {
        // Threads are pooled. A leaked MDC would stamp these fields onto every unrelated line the
        // same worker writes next, making one event look like it happened over and over.
        activityLog.logActivity("HotelSearchModule", "searchHotels", "destination=Antalya",
                "SUCCESS", "Hotel search completed");

        assertThat(org.slf4j.MDC.getCopyOfContextMap()).isNullOrEmpty();
    }

    @Test
    void logActivity_withoutRequestData_stillLogsTheEvent() {
        activityLog.logActivity("HotelSearchModule", "searchHotels", null, "SUCCESS", "done");

        ILoggingEvent event = onlyEvent();
        assertThat(event.getMDCPropertyMap()).doesNotContainKey("activity.request");
        assertThat(event.getMDCPropertyMap()).containsEntry("activity.module", "HotelSearchModule");
    }

    private ILoggingEvent onlyEvent() {
        List<ILoggingEvent> events = appender.list;
        assertThat(events).hasSize(1);
        return events.get(0);
    }
}
