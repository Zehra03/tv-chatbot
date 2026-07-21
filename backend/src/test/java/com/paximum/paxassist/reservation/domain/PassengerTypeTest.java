package com.paximum.paxassist.reservation.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The age bands now live on the enum and {@code PreviewReservationCommand} only applies them, so these
 * numbers are the ONE place a band is stated on the backend. They are also mirrored by the frontend
 * form schema (guarded there by {@code passengerAgeBands.contract.test.ts}), which reads this file —
 * changing a band here is a contract change, not a local edit.
 */
class PassengerTypeTest {

    @Test
    void bandsCoverEveryAgeWithoutGapsOrOverlap() {
        assertThat(PassengerType.INFANT.minAge()).isZero();
        assertThat(PassengerType.INFANT.maxAge() + 1).isEqualTo(PassengerType.CHILD.minAge());
        assertThat(PassengerType.CHILD.maxAge() + 1).isEqualTo(PassengerType.ADULT.minAge());
        assertThat(PassengerType.ADULT.maxAge()).isEqualTo(PassengerType.MAX_AGE);
    }

    @Test
    void matchesAgeAcceptsOnlyItsOwnBand() {
        assertThat(PassengerType.INFANT.matchesAge(2)).isTrue();
        assertThat(PassengerType.INFANT.matchesAge(3)).isFalse();

        assertThat(PassengerType.CHILD.matchesAge(3)).isTrue();
        assertThat(PassengerType.CHILD.matchesAge(17)).isTrue();
        assertThat(PassengerType.CHILD.matchesAge(2)).isFalse();
        assertThat(PassengerType.CHILD.matchesAge(18)).isFalse();

        assertThat(PassengerType.ADULT.matchesAge(18)).isTrue();
        assertThat(PassengerType.ADULT.matchesAge(120)).isTrue();
        assertThat(PassengerType.ADULT.matchesAge(17)).isFalse();
        assertThat(PassengerType.ADULT.matchesAge(121)).isFalse();
    }

    @Test
    void jsonValuesStayLowercaseForTheWireContract() {
        assertThat(PassengerType.ADULT.toJson()).isEqualTo("adult");
        assertThat(PassengerType.fromJson("Infant")).isEqualTo(PassengerType.INFANT);
    }
}
