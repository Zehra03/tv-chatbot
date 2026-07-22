package com.paximum.paxassist.flight.event;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import com.paximum.paxassist.common.log.ActivityLog;

/**
 * Turns {@link FlightSearchEvent} into an activity log line, so flight searches show up in the log
 * stream with the same {@code module}/{@code action}/{@code status} fields as hotel searches and
 * reservations.
 *
 * <p>Until now the flight module published this event and <em>nothing subscribed to it</em> — the
 * search outcome was raised and dropped on the floor, which is why the architecture diagram claimed
 * flight sent log events while the log stream had none. The event itself was already the right
 * shape; all it lacked was a listener.
 *
 * <p>Listening rather than injecting {@link ActivityLog} into the search service keeps the module's
 * business code free of logging concerns and leaves the event available to any future subscriber
 * (metrics, analytics) without another edit to the service.
 *
 * <p>Synchronous on purpose: the listener only formats a string and writes a line, and running it on
 * the publishing thread keeps the activity line adjacent to the search's own logs and inside the
 * same correlation context ({@code requestId}/{@code userId} live in the request thread's MDC — an
 * {@code @Async} hop would lose them, which is the whole point of having them).
 */
@Component
public class FlightSearchActivityLogger {

    private static final String MODULE = "FlightSearchModule";
    private static final String ACTION = "searchFlights";

    private final ActivityLog activityLog;

    public FlightSearchActivityLogger(ActivityLog activityLog) {
        this.activityLog = activityLog;
    }

    @EventListener
    public void onFlightSearch(FlightSearchEvent event) {
        // Route and trip type only: a flight search carries no passenger identity, and the traveller
        // counts add nothing a support question would need.
        String route = event.origin() + "->" + event.destination() + " (" + event.tripType() + ")";
        if (event.success()) {
            activityLog.logActivity(MODULE, ACTION, route, "SUCCESS",
                    "Flight search returned " + event.resultCount() + " result(s)");
        } else {
            activityLog.logActivity(MODULE, ACTION, route, "FAILED",
                    "Flight search failed at the provider");
        }
    }
}
