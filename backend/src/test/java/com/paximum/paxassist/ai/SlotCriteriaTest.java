package com.paximum.paxassist.ai;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * Guards the flight-filter slots on {@link SlotCriteria}: the constructor must store
 * {@code airline} and {@code departTimeRange} (alongside the existing {@code directFlight}), and
 * {@code empty()} must leave them {@code null}.
 */
class SlotCriteriaTest {

    @Test
    void constructorStoresFlightFilterSlots() {
        SlotCriteria criteria = new SlotCriteria(
                null, null, null, null, null, null, null, null, null, null,
                "İstanbul", "İzmir", null, null, null, null, Boolean.TRUE,
                "THY", "morning", null, null,
                null, null, null, null, null, null, null, null);

        assertThat(criteria.directFlight()).isTrue();
        assertThat(criteria.airline()).isEqualTo("THY");
        assertThat(criteria.departTimeRange()).isEqualTo("morning");
    }

    @Test
    void emptyLeavesFlightFilterSlotsNull() {
        SlotCriteria criteria = SlotCriteria.empty();

        assertThat(criteria.airline()).isNull();
        assertThat(criteria.departTimeRange()).isNull();
        assertThat(criteria.directFlight()).isNull();
    }
}
