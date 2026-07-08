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
        return new SlotCriteria(
                pick(update.location(), base.location()),
                pick(update.checkIn(), base.checkIn()),
                pick(update.checkOut(), base.checkOut()),
                pick(update.nights(), base.nights()),
                pick(update.rooms(), base.rooms()),
                pick(update.stars(), base.stars()),
                pick(update.boardType(), base.boardType()),
                pick(update.origin(), base.origin()),
                pick(update.destination(), base.destination()),
                pick(update.departureDate(), base.departureDate()),
                pick(update.returnDate(), base.returnDate()),
                pick(update.cabinClass(), base.cabinClass()),
                pick(update.adults(), base.adults()),
                pick(update.children(), base.children()),
                pick(update.childAges(), base.childAges()),
                pick(update.nationality(), base.nationality()),
                pick(update.currency(), base.currency()),
                pick(update.maxPrice(), base.maxPrice()),
                pick(update.sortBy(), base.sortBy()),
                pick(update.selectionReference(), base.selectionReference())
        );
    }

    private static <T> T pick(T update, T base) {
        return update != null ? update : base;
    }

    private static List<Integer> pick(List<Integer> update, List<Integer> base) {
        return (update != null && !update.isEmpty()) ? update : base;
    }
}
