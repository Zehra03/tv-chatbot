package com.paximum.paxassist.flight.domain;

import java.util.List;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class PassengerCount {
    private final int adults;
    private final int children;
    private final int infants;

    /** Oldest age still carried as a lap infant; from age 3 an airline seats and prices a child. */
    private static final int INFANT_MAX_AGE = 2;
    /** Oldest age still priced as a child; from 12 an airline charges the adult fare. */
    private static final int CHILD_MAX_AGE = 11;

    /**
     * Most seated passengers (adults + children) one booking may hold — the airline/GDS standard.
     * Lap infants are excluded because they occupy no seat.
     */
    public static final int MAX_SEATS = 9;

    /** Adults + children: everyone who occupies a seat, which is what {@link #MAX_SEATS} caps. */
    public int seatedCount() {
        return adults + children;
    }

    /** True when this party needs more seats than one booking can hold. */
    public boolean exceedsSeatLimit() {
        return seatedCount() > MAX_SEATS;
    }

    /**
     * True when there are more lap infants than laps to carry them: each infant flies on an adult's
     * lap, so a party can never hold more infants than adults. Caught here rather than at the
     * provider, where it surfaces as an opaque failure part-way into booking.
     */
    public boolean hasMoreInfantsThanAdults() {
        return infants > adults;
    }

    /**
     * Types each accompanying child by AGE, which is what the fare depends on: TourVisio prices
     * passenger types 1/2/3 (adult/child/infant) differently, so a 1-year-old sent as a child gets
     * quoted a price the traveller cannot book at. A child of 12 or older pays the adult fare and is
     * counted as an adult here.
     *
     * <p>The ages are the only input: a bare child COUNT cannot be typed, so a caller has to obtain
     * the ages rather than assume a type. The rule lives in the flight domain because it is the
     * provider's fare rule — every search surface (chat, MCP) must apply the same one.
     */
    public static PassengerCount ofChildAges(int adults, List<Integer> childAges) {
        List<Integer> ages = (childAges == null) ? List.of() : childAges;
        int infants = (int) ages.stream().filter(age -> age != null && age <= INFANT_MAX_AGE).count();
        int children = (int) ages.stream()
                .filter(age -> age != null && age > INFANT_MAX_AGE && age <= CHILD_MAX_AGE).count();
        int adultFareChildren = (int) ages.stream().filter(age -> age != null && age > CHILD_MAX_AGE).count();

        return PassengerCount.builder()
                .adults(adults + adultFareChildren)
                .children(children)
                .infants(infants)
                .build();
    }
}
