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

    @Test
    void shouldComputeNightsFromCheckOut() {
        String checkIn = LocalDate.now().plusDays(2).toString();
        String checkOut = LocalDate.now().plusDays(5).toString();
        SlotCriteria criteria = new SlotCriteria(
                null, checkIn, checkOut, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null
        );

        SlotCriteria normalized = normalizer.normalize(criteria);

        assertThat(normalized.checkOut()).isEqualTo(checkOut);
        assertThat(normalized.nights()).isEqualTo(3);
    }

    @Test
    void shouldFixReverseCheckOut() {
        String checkIn = LocalDate.now().plusDays(5).toString();
        String checkOut = LocalDate.now().plusDays(2).toString();
        SlotCriteria criteria = new SlotCriteria(
                null, checkIn, checkOut, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null
        );

        SlotCriteria normalized = normalizer.normalize(criteria);

        assertThat(normalized.checkOut()).isEqualTo(LocalDate.now().plusDays(6).toString());
        assertThat(normalized.nights()).isEqualTo(1);
    }

    @Test
    void shouldFixReverseReturnDate() {
        String depart = LocalDate.now().plusDays(5).toString();
        String returnDate = LocalDate.now().plusDays(2).toString();
        SlotCriteria criteria = new SlotCriteria(
                null, null, null, null, null, null, null, null, null,
                null, null, depart, returnDate, null, null, null, null,
                null, null, null, null, null, null, null
        );

        SlotCriteria normalized = normalizer.normalize(criteria);

        assertThat(normalized.returnDate()).isEqualTo(LocalDate.now().plusDays(6).toString());
    }
}
