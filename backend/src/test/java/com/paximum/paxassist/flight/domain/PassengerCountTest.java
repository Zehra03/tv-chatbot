package com.paximum.paxassist.flight.domain;

import java.util.List;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PassengerCountTest {

    @Test
    void typesChildrenByAge() {
        PassengerCount party = PassengerCount.ofChildAges(2, List.of(1, 8, 14));

        assertThat(party.getInfants()).isEqualTo(1);
        assertThat(party.getChildren()).isEqualTo(1);
        assertThat(party.getAdults()).isEqualTo(3);
    }

    @Test
    void ageBoundaries() {
        PassengerCount party = PassengerCount.ofChildAges(0, List.of(0, 1, 2, 11, 12));

        assertThat(party.getInfants()).isEqualTo(2);   // 0, 1
        assertThat(party.getChildren()).isEqualTo(2);  // 2, 11
        assertThat(party.getAdults()).isEqualTo(1);    // 12
    }

    @Test
    void noAges_meansNoAccompanyingChildren() {
        assertThat(PassengerCount.ofChildAges(2, null).getChildren()).isZero();
        assertThat(PassengerCount.ofChildAges(2, List.of()).getInfants()).isZero();
    }

    @Test
    void seatedCountExcludesLapInfants() {
        PassengerCount party = PassengerCount.ofChildAges(2, List.of(0, 8));

        assertThat(party.seatedCount()).isEqualTo(3); // 2 adults + the 8-year-old
    }

    @Test
    void nineSeatsFit_tenDoNot() {
        assertThat(PassengerCount.ofChildAges(9, List.of()).exceedsSeatLimit()).isFalse();
        assertThat(PassengerCount.ofChildAges(10, List.of()).exceedsSeatLimit()).isTrue();
        // 8 adults + one seated child = 9 seats; the two infants take none, so it still fits.
        assertThat(PassengerCount.ofChildAges(8, List.of(5, 1, 0)).exceedsSeatLimit()).isFalse();
    }

    @Test
    void aChildTurning12CanPushThePartyOverTheSeatLimit() {
        // 9 adults + a 12-year-old = 10 adult-fare seats, even though only 9 people were "adults".
        assertThat(PassengerCount.ofChildAges(9, List.of(12)).exceedsSeatLimit()).isTrue();
    }

    @Test
    void infantsMayNotOutnumberAdults() {
        assertThat(PassengerCount.ofChildAges(1, List.of(0, 1)).hasMoreInfantsThanAdults()).isTrue();
        assertThat(PassengerCount.ofChildAges(2, List.of(0, 1)).hasMoreInfantsThanAdults()).isFalse();
    }
}
