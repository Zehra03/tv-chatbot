package com.paximum.paxassist.flight.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BaggageAllowanceTest {

    private final BaggageAllowance checked20 = new BaggageAllowance(true, 20);
    private final BaggageAllowance checked15 = new BaggageAllowance(true, 15);
    private final BaggageAllowance cabinOnly = new BaggageAllowance(false, null);
    /** Checked baggage stated per piece rather than per kg — included, but with no kg to compare. */
    private final BaggageAllowance checkedByPiece = new BaggageAllowance(true, null);

    @Test
    void withNoRequest_everyAllowanceQualifies_evenAnUnknownOne() {
        assertThat(checked20.satisfies(null, null)).isTrue();
        assertThat(cabinOnly.satisfies(null, null)).isTrue();
        assertThat(BaggageAllowance.unknown().satisfies(null, null)).isTrue();
    }

    @Test
    void requiringCheckedBaggage_keepsFaresThatIncludeIt() {
        assertThat(checked20.satisfies(true, null)).isTrue();
        assertThat(checkedByPiece.satisfies(true, null)).isTrue();
        assertThat(cabinOnly.satisfies(true, null)).isFalse();
    }

    @Test
    void askingForNoCheckedBaggage_keepsOnlyTheCabinOnlyFare() {
        assertThat(cabinOnly.satisfies(false, null)).isTrue();
        assertThat(checked20.satisfies(false, null)).isFalse();
    }

    @Test
    void aKgThreshold_isMetOnlyAtOrAboveIt() {
        assertThat(checked20.satisfies(null, 20)).isTrue();
        assertThat(checked20.satisfies(null, 15)).isTrue();
        assertThat(checked15.satisfies(null, 20)).isFalse();
        assertThat(cabinOnly.satisfies(null, 15)).isFalse();
    }

    @Test
    void aPieceBasedAllowance_cannotMeetAKgThreshold() {
        // There is no weight to compare, so claiming it carries 20 kg would be a guess.
        assertThat(checkedByPiece.satisfies(null, 20)).isFalse();
    }

    @Test
    void anUnknownAllowance_neverMeetsARequest() {
        // The fare may well include 20 kg — but the provider did not say so, and a card must not
        // answer "20 kg bagajlı" with a fare nobody verified.
        assertThat(BaggageAllowance.unknown().satisfies(true, null)).isFalse();
        assertThat(BaggageAllowance.unknown().satisfies(false, null)).isFalse();
        assertThat(BaggageAllowance.unknown().satisfies(null, 15)).isFalse();
    }
}
