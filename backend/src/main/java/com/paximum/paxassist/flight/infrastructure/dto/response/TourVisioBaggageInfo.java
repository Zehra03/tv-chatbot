package com.paximum.paxassist.flight.infrastructure.dto.response;

/**
 * One baggage allowance line of a TourVisio offer (or, in payloads that state it per segment, of a
 * flight item).
 *
 * <p>{@code baggageType} tells checked from cabin baggage: {@code 1} is checked, {@code 2} is
 * carry-on. The provider's docs link this enum out to a page we do not have, so the reading comes
 * from the payloads themselves — every {@code 1} in them is a 15/20 kg allowance sitting next to a
 * service named "Checked baggage allowance 15 kg", and every {@code 2} an 8 kg one named "Carry on
 * baggage allowance 8 kg". Baggage filtering leans on this, so it is worth confirming against the
 * enum page when we have it.
 *
 * <p>{@code unitType} says how to read the allowance: {@code 0} is a weight in kg ({@code weight}),
 * {@code 2} is a piece count ({@code piece}) with no weight at all. The fields are boxed so an
 * absent one stays distinguishable from a stated zero — "no weight given" and "0 kg" must not be
 * confused once a kg threshold is applied to them.
 */
public record TourVisioBaggageInfo(
        Integer weight,
        Integer piece,
        Integer baggageType,
        Integer unitType) {

    /** Checked (hold) baggage — what "bagaj dahil mi" means to a traveller. */
    public static final int TYPE_CHECKED = 1;

    /** The allowance is a weight in kg; otherwise the provider states it as a number of pieces. */
    private static final int UNIT_KG = 0;

    public boolean isChecked() {
        return baggageType != null && baggageType == TYPE_CHECKED;
    }

    /** This line's allowance in kg, or null when it was stated per piece or not at all. */
    public Integer weightInKg() {
        boolean statedInKg = (unitType == null || unitType == UNIT_KG);
        return (statedInKg && weight != null && weight > 0) ? weight : null;
    }

    /** True when the line grants anything: a 0 kg / 0 piece line is an allowance of nothing. */
    public boolean grantsAllowance() {
        return (weight != null && weight > 0) || (piece != null && piece > 0);
    }
}
