package com.paximum.paxassist.orchestrator.intent;

import java.util.List;

import com.paximum.paxassist.ai.SlotCriteria;

/**
 * Wording for the one case where Paxi reuses something the user said about a different search:
 * after switching domain ("vazgeçtim, uçak arıyorum") the traveller count carries over, so the
 * reply says so once instead of either asking again or silently assuming.
 *
 * <p>Deliberately a statement, not a question ("2 yetişkin aldım, değiştirmek istersen söyle"):
 * the user can correct it in their next message like any other slot, and the turn is not spent on
 * a confirmation.
 */
final class TravellerCarryOver {

    private TravellerCarryOver() {
    }

    /**
     * @param switchedDomain whether this turn switched the conversation to another product domain
     * @param turnCriteria   the slots extracted from THIS message (null when none)
     * @param merged         the accumulated slots after merging
     * @return a sentence to append to the reply, or an empty string when there is nothing to say
     */
    static String note(boolean switchedDomain, SlotCriteria turnCriteria, SlotCriteria merged) {
        if (!switchedDomain || merged == null || merged.adults() == null) {
            return "";
        }
        // The user just restated the count for the new search, so it was not carried over.
        if (turnCriteria != null && turnCriteria.adults() != null) {
            return "";
        }
        return " (Kişi sayısını az önceki aramandan aldım: " + describe(merged)
                + ". Değiştirmek istersen söylemen yeterli.)";
    }

    private static String describe(SlotCriteria merged) {
        StringBuilder text = new StringBuilder(merged.adults() + " yetişkin");
        int children = childCount(merged);
        if (children > 0) {
            text.append(", ").append(children).append(" çocuk");
        }
        return text.toString();
    }

    /** Ages are the more precise signal; the plain count is the fallback when no ages were given. */
    private static int childCount(SlotCriteria merged) {
        List<Integer> ages = merged.childAges();
        if (ages != null && !ages.isEmpty()) {
            return ages.size();
        }
        return merged.children() != null ? merged.children() : 0;
    }
}
