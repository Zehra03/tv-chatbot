package com.paximum.paxassist.orchestrator.slot;

import java.util.List;

import org.springframework.stereotype.Component;

import com.paximum.paxassist.ai.SlotCriteria;

/**
 * Pure, dependency-free merge of two {@link SlotCriteria} turns.
 * Rule: a non-null field on {@code update} wins; otherwise the accumulated {@code base}
 * value is kept. This is what makes multi-turn slot-filling work — each turn contributes
 * only the fields the user just mentioned, without wiping what was said before.
 *
 * <p>Kept pure (no Spring/Jackson) so the merge rule is trivially unit-testable.
 */
@Component
public class SlotMerger {

    public SlotCriteria merge(SlotCriteria base, SlotCriteria update) {
        if (update == null) {
            // Never return null: a turn may carry an intent with no slots (e.g. "otel arıyorum"),
            // and the mappers dereference the result — so coalesce both-null to an empty criteria.
            return base != null ? base : SlotCriteria.empty();
        }
        if (base == null) {
            return update;
        }
        String mergedCheckOut = pick(update.checkOut(), base.checkOut());
        Integer mergedNights = pick(update.nights(), base.nights());

        boolean checkInUpdated = update.checkIn() != null && !update.checkIn().isBlank();
        boolean checkOutUpdated = update.checkOut() != null && !update.checkOut().isBlank();
        boolean nightsUpdated = update.nights() != null;

        if (nightsUpdated) {
            if (!checkOutUpdated) {
                mergedCheckOut = null;
            }
        } else if (checkInUpdated || checkOutUpdated) {
            mergedNights = null;
        }

        return new SlotCriteria(
                pick(update.location(), base.location()),
                pick(update.checkIn(), base.checkIn()),
                mergedCheckOut,
                mergedNights,
                pick(update.rooms(), base.rooms()),
                pick(update.stars(), base.stars()),
                pick(update.maxStars(), base.maxStars()),
                pick(update.boardType(), base.boardType()),
                pick(update.features(), base.features()),
                pick(update.hotelMaxPrice(), base.hotelMaxPrice()),
                pick(update.origin(), base.origin()),
                pick(update.destination(), base.destination()),
                pick(update.departureDate(), base.departureDate()),
                pick(update.returnDate(), base.returnDate()),
                pick(update.cabinClass(), base.cabinClass()),
                pick(update.flightMaxPrice(), base.flightMaxPrice()),
                pick(update.directFlight(), base.directFlight()),
                pick(update.airline(), base.airline()),
                pick(update.departTimeRange(), base.departTimeRange()),
                pick(update.checkedBaggage(), base.checkedBaggage()),
                pick(update.minCheckedBaggageKg(), base.minCheckedBaggageKg()),
                pick(update.tripType(), base.tripType()),
                pick(update.adults(), base.adults()),
                pick(update.children(), base.children()),
                pick(update.childAges(), base.childAges()),
                pick(update.nationality(), base.nationality()),
                pick(update.currency(), base.currency()),
                pick(update.sortBy(), base.sortBy()),
                pick(update.limit(), base.limit()),
                pick(update.selectionReference(), base.selectionReference())
        );
    }

    private static <T> T pick(T update, T base) {
        if (update == null) {
            return base;
        }
        if (update instanceof String s && s.isBlank()) {
            return base;
        }
        return update;
    }

    // Lists (childAges, features): a non-empty update wins; empty/null keeps the accumulated value.
    private static <E> List<E> pick(List<E> update, List<E> base) {
        return (update != null && !update.isEmpty()) ? update : base;
    }
}
