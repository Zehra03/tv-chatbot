package com.paximum.paxassist.flight.domain;

/**
 * What a fare includes in checked baggage, and the rule for deciding whether it meets what the
 * traveller asked for.
 *
 * <p>Three states matter, and collapsing them would make the bot lie:
 * <ul>
 *   <li>{@code checkedIncluded = true} — the fare includes checked baggage;</li>
 *   <li>{@code checkedIncluded = false} — the provider listed this fare's baggage and none of it is
 *       checked (a cabin-only fare);</li>
 *   <li>{@code checkedIncluded = null} — the provider said nothing, so we do not know.</li>
 * </ul>
 *
 * <p>An unknown allowance never satisfies a baggage request: a fare we cannot verify must not be
 * presented as "20 kg bagajlı". It is only dropped when the traveller actually asked about baggage —
 * with no request, an unknown allowance is just a card without that detail.
 */
public record BaggageAllowance(Boolean checkedIncluded, Integer checkedKg) {

    /** The provider told us nothing about this fare's baggage. */
    public static BaggageAllowance unknown() {
        return new BaggageAllowance(null, null);
    }

    public boolean isUnknown() {
        return checkedIncluded == null;
    }

    /**
     * @param requireChecked true = the fare must include checked baggage, false = it must NOT (the
     *                       traveller wants the cabin-only fare), null = no preference
     * @param minCheckedKg   the fare's checked allowance must be at least this many kg; null = no
     *                       threshold. A piece-based allowance has no kg to compare and therefore
     *                       cannot meet a kg threshold.
     */
    public boolean satisfies(Boolean requireChecked, Integer minCheckedKg) {
        if (requireChecked == null && minCheckedKg == null) {
            return true;
        }
        if (isUnknown()) {
            return false;
        }
        if (requireChecked != null && !requireChecked.equals(checkedIncluded)) {
            return false;
        }
        if (minCheckedKg != null) {
            return Boolean.TRUE.equals(checkedIncluded) && checkedKg != null && checkedKg >= minCheckedKg;
        }
        return true;
    }
}
