package com.paximum.paxassist.orchestrator.slot;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.paximum.paxassist.ai.SlotCriteria;

class SlotNormalizerTest {

    private SlotNormalizer normalizer;

    @BeforeEach
    void setUp() {
        normalizer = new SlotNormalizer();
    }

    @Test
    void shouldComputeNightsFromCheckOut() {
        String checkIn = LocalDate.now().plusDays(2).toString();
        String checkOut = LocalDate.now().plusDays(5).toString();
        SlotCriteria criteria = new SlotCriteria(
                null, checkIn, checkOut, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null,
                null, null, null
        );

        SlotCriteria normalized = normalizer.normalize(criteria);

        assertThat(normalized.checkOut()).isEqualTo(checkOut);
        assertThat(normalized.nights()).isEqualTo(3);
    }

    @Test
    void shouldClearReverseCheckOut() {
        String checkIn = LocalDate.now().plusDays(5).toString();
        String checkOut = LocalDate.now().plusDays(2).toString();
        SlotCriteria criteria = new SlotCriteria(
                null, checkIn, checkOut, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null,
                null, null, null
        );

        SlotCriteria normalized = normalizer.normalize(criteria);

        assertThat(normalized.checkOut()).isNull();
        assertThat(normalized.nights()).isNull();
    }

    @Test
    void shouldClearReverseReturnDate() {
        String depart = LocalDate.now().plusDays(5).toString();
        String returnDate = LocalDate.now().plusDays(2).toString();
        SlotCriteria criteria = new SlotCriteria(
                null, null, null, null, null, null, null, null, null, null,
                null, null, depart, returnDate, null, null, null, null, null, null, null,
                null, null, null, null, null,
                null, null, null
        );

        SlotCriteria normalized = normalizer.normalize(criteria);

        assertThat(normalized.returnDate()).isNull();
    }

    @Test
    void shouldClearPastDates() {
        String pastCheckIn = LocalDate.now().minusDays(5).toString();
        String pastCheckOut = LocalDate.now().minusDays(2).toString();
        String pastDepart = LocalDate.now().minusDays(10).toString();
        String pastReturn = LocalDate.now().minusDays(1).toString();

        SlotCriteria criteria = new SlotCriteria(
                null, pastCheckIn, pastCheckOut, null, null, null, null, null, null, null,
                null, null, pastDepart, pastReturn, null, null, null, null, null, null, null,
                null, null, null, null, null,
                null, null, null
        );

        SlotCriteria normalized = normalizer.normalize(criteria);

        assertThat(normalized.checkIn()).isNull();
        assertThat(normalized.checkOut()).isNull();
        assertThat(normalized.departureDate()).isNull();
        assertThat(normalized.returnDate()).isNull();
    }

    @Test
    void shouldClearInvalidNumericValues() {
        SlotCriteria criteria = new SlotCriteria(
                null, null, null, null, 0, null, null, null, null, -100,
                null, null, null, null, null, -50, null, null, null, null, null,
                0, -1, java.util.List.of(5, -2, 8), null, null,
                null, null, null
        );

        SlotCriteria normalized = normalizer.normalize(criteria);

        assertThat(normalized.rooms()).isNull();
        assertThat(normalized.hotelMaxPrice()).isNull();
        assertThat(normalized.flightMaxPrice()).isNull();
        assertThat(normalized.adults()).isNull();
        assertThat(normalized.children()).isNull();
        assertThat(normalized.childAges()).containsExactly(5, 8);
    }

    @Test
    void shouldDropChildAgesOutsideZeroToSeventeen() {
        SlotCriteria criteria = new SlotCriteria(
                null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null, null,
                null, null, java.util.List.of(5, 25, 17, -3), null, null,
                null, null, null
        );

        SlotCriteria normalized = normalizer.normalize(criteria);

        assertThat(normalized.childAges()).containsExactly(5, 17);
    }

    @Test
    void shouldPreserveFlightFilterSlots() {
        // airline / departTimeRange carry no date/number logic — normalize must pass them through
        // untouched (alongside directFlight) so the flight search can act on them.
        String depart = LocalDate.now().plusDays(3).toString();
        SlotCriteria criteria = new SlotCriteria(
                null, null, null, null, null, null, null, null, null, null,
                "İstanbul", "İzmir", depart, null, null, null, Boolean.TRUE, "THY", "morning", null, null,
                null, null, null, null, null,
                null, null, null
        );

        SlotCriteria normalized = normalizer.normalize(criteria);

        assertThat(normalized.directFlight()).isTrue();
        assertThat(normalized.airline()).isEqualTo("THY");
        assertThat(normalized.departTimeRange()).isEqualTo("morning");
    }
}
