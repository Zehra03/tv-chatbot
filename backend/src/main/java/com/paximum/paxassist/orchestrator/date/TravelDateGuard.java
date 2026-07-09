package com.paximum.paxassist.orchestrator.date;

import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Optional;

import org.springframework.stereotype.Component;

import com.paximum.paxassist.ai.SlotCriteria;

/**
 * Deterministic past-date guard for the chat search flow.
 *
 * <p>The friendly "tarih geçmişte kalıyor" script only lives in the conversational Paxi prompt,
 * which the HOTEL/FLIGHT search intents bypass entirely. A small 4B model also can't be trusted
 * to reject past dates reliably. So this guard checks the merged {@link SlotCriteria} dates against
 * "today" before any TourVisio call and, if a date is in the past, returns a ready clarifying
 * message the handler turns into an {@code OrchestrationResult.clarify(...)}.
 *
 * <p>Uses an injectable {@link Clock} (default system clock) so the check is unit-testable with a
 * fixed date. No Spring bean is required — the no-arg constructor wires the system clock.
 */
@Component
public class TravelDateGuard {

    private final Clock clock;

    public TravelDateGuard() {
        this(Clock.systemDefaultZone());
    }

    // Spring uses the no-arg constructor (system clock). This one lets tests inject a fixed clock
    // for deterministic past/future-date assertions.
    public TravelDateGuard(Clock clock) {
        this.clock = clock;
    }

    /**
     * @return a clarifying message if any provided date (check-in/out, departure/return) is before
     *         today; otherwise {@link Optional#empty()}. Unparsable/blank dates are ignored (the
     *         module's own missing-field handling deals with those).
     */
    public Optional<String> checkPastDate(SlotCriteria criteria) {
        if (criteria == null) {
            return Optional.empty();
        }
        LocalDate today = LocalDate.now(clock);
        // Primary dates first so the message points at the field the user most likely meant.
        for (String raw : new String[] {
                criteria.checkIn(), criteria.departureDate(), criteria.checkOut(), criteria.returnDate()}) {
            LocalDate date = parse(raw);
            if (date != null && date.isBefore(today)) {
                return Optional.of(
                        "Girdiğiniz tarih (" + date + ") geçmişte kalıyor. İleri bir tarih verir misiniz?");
            }
        }
        return Optional.empty();
    }

    private LocalDate parse(String date) {
        if (date == null || date.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(date.trim());
        } catch (DateTimeParseException e) {
            return null;
        }
    }
}
