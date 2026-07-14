package com.paximum.paxassist.orchestrator.slot;

import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Optional;

import org.springframework.stereotype.Component;

import com.paximum.paxassist.ai.SlotCriteria;

/**
 * Deterministic past-date guard for the chat search flow.
 *
 * <p>
 * The friendly "tarih geçmişte kalıyor" script only lives in the conversational
 * Paxi prompt,
 * which the HOTEL/FLIGHT search intents bypass entirely. A small 4B model also
 * can't be trusted
 * to reject past dates reliably. So this guard checks the merged
 * {@link SlotCriteria} dates against
 * "today" before any TourVisio call and, if a date is in the past, returns a
 * ready clarifying
 * message the handler turns into an {@code OrchestrationResult.clarify(...)}.
 *
 * <p>
 * Uses an injectable {@link Clock} (default system clock) so the check is
 * unit-testable with a
 * fixed date. No Spring bean is required — the no-arg constructor wires the
 * system clock.
 */
@Component
public class SlotGuard {

    private final Clock clock;

    public SlotGuard() {
        this(Clock.systemDefaultZone());
    }

    // Spring uses the no-arg constructor (system clock). This one lets tests inject
    // a fixed clock
    // for deterministic past/future-date assertions.
    public SlotGuard(Clock clock) {
        this.clock = clock;
    }

    /**
     * @return a clarifying message if any provided date (check-in/out,
     *         departure/return) is before
     *         today; otherwise {@link Optional#empty()}. Unparsable/blank dates are
     *         ignored (the
     *         module's own missing-field handling deals with those).
     */
    public Optional<String> checkInvalidSlots(SlotCriteria criteria) {
        if (criteria == null) {
            return Optional.empty();
        }
        LocalDate today = LocalDate.now(clock);
        // Primary dates first so the message points at the field the user most likely
        // meant.
        for (String raw : new String[] {
                criteria.checkIn(), criteria.departureDate(), criteria.checkOut(), criteria.returnDate() }) {
            LocalDate date = parse(raw);
            if (date != null && date.isBefore(today)) {
                return Optional.of(
                        "Girdiğiniz tarih (" + date + ") geçmişte kalıyor. İleri bir tarih verir misiniz?");
            }
        }

        if (criteria.adults() != null && criteria.adults() <= 0) {
            return Optional.of("Yetişkin sayısı en az 1 olmalıdır. Lütfen geçerli bir kişi sayısı giriniz.");
        }
        if (criteria.children() != null && criteria.children() < 0) {
            return Optional.of("Çocuk sayısı negatif olamaz. Lütfen geçerli bir çocuk sayısı giriniz.");
        }
        if (criteria.childAges() != null && criteria.childAges().stream().anyMatch(age -> age < 0)) {
            return Optional.of("Çocuk yaşları negatif olamaz. Lütfen geçerli yaşlar giriniz.");
        }
        if (criteria.rooms() != null && criteria.rooms() <= 0) {
            return Optional.of("Oda sayısı en az 1 olmalıdır. Lütfen geçerli bir oda sayısı giriniz.");
        }
        if (criteria.hotelMaxPrice() != null && criteria.hotelMaxPrice() <= 0) {
            return Optional.of("Bütçe 0'dan büyük olmalıdır. Lütfen geçerli bir bütçe giriniz.");
        }
        if (criteria.flightMaxPrice() != null && criteria.flightMaxPrice() <= 0) {
            return Optional.of("Bütçe 0'dan büyük olmalıdır. Lütfen geçerli bir bütçe giriniz.");
        }
        if (criteria.nights() != null && criteria.nights() <= 0) {
            return Optional.of("Gece sayısı en az 1 olmalıdır. Lütfen geçerli bir gece sayısı giriniz.");
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
