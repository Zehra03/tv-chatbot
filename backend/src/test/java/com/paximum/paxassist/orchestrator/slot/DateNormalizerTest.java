package com.paximum.paxassist.orchestrator.slot;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.paximum.paxassist.ai.SlotCriteria;

class DateNormalizerTest {

    private DateNormalizer normalizer;

    @BeforeEach
    void setUp() {
        normalizer = new DateNormalizer();
    }

    /** Only the stay dates matter here; every other slot stays empty. */
    private SlotCriteria stayOn(String checkIn, String checkOut) {
        return new SlotCriteria(
                null, checkIn, checkOut, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null, null);
    }

    /** Only the flight dates matter here; every other slot stays empty. */
    private SlotCriteria flightOn(String departureDate, String returnDate) {
        return new SlotCriteria(
                null, null, null, null, null, null, null, null, null, null, null,
                departureDate, returnDate, null, null, null, null, null, null, null, null, null);
    }

    @Test
    void shouldComputeNightsFromCheckOut() {
        String checkIn = LocalDate.now().plusDays(2).toString();
        String checkOut = LocalDate.now().plusDays(5).toString();

        SlotCriteria normalized = normalizer.normalize(stayOn(checkIn, checkOut));

        assertThat(normalized.checkOut()).isEqualTo(checkOut);
        assertThat(normalized.nights()).isEqualTo(3);
    }

    @Test
    void shouldFixReverseCheckOut() {
        String checkIn = LocalDate.now().plusDays(5).toString();
        String checkOut = LocalDate.now().plusDays(2).toString();

        SlotCriteria normalized = normalizer.normalize(stayOn(checkIn, checkOut));

        assertThat(normalized.checkOut()).isEqualTo(LocalDate.now().plusDays(6).toString());
        assertThat(normalized.nights()).isEqualTo(1);
    }

    @Test
    void shouldFixReverseReturnDate() {
        String depart = LocalDate.now().plusDays(5).toString();
        String returnDate = LocalDate.now().plusDays(2).toString();

        SlotCriteria normalized = normalizer.normalize(flightOn(depart, returnDate));

        assertThat(normalized.returnDate()).isEqualTo(LocalDate.now().plusDays(6).toString());
    }

    /**
     * The hotel and flight budgets are both {@code Integer} and sit in a 22-component positional
     * constructor, so a misordered rebuild would compile happily and silently swap the two. Pin
     * them to their own slots.
     */
    @Test
    void shouldCarryHotelAndFlightBudgetsThroughSeparately() {
        SlotCriteria criteria = new SlotCriteria(
                null, null, null, null, null, null, null, null, 18000, null, null,
                null, null, null, 3000, null, null, null, null, null, null, null);

        SlotCriteria normalized = normalizer.normalize(criteria);

        assertThat(normalized.hotelMaxPrice()).isEqualTo(18000);
        assertThat(normalized.flightMaxPrice()).isEqualTo(3000);
    }
}
