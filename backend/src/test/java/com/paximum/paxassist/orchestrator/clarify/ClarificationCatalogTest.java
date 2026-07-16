package com.paximum.paxassist.orchestrator.clarify;

import java.util.List;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ClarificationCatalogTest {

    private final ClarificationCatalog catalog = new ClarificationCatalog();

    @Test
    void asksForFirstMissingHotelField() {
        String question = catalog.questionForHotel(List.of("night", "adult"));
        assertThat(question).contains("gece");
    }

    @Test
    void asksForFirstMissingFlightField() {
        String question = catalog.questionForFlight(List.of("destination", "departDate"));
        assertThat(question).contains("Nereye");
    }

    @Test
    void asksForChildAgesWhenMissing() {
        String question = catalog.questionForHotel(List.of("childAges"));
        assertThat(question).contains("yaş");
    }

    @Test
    void fallsBackWhenNoMissingFields() {
        assertThat(catalog.questionForHotel(List.of())).isNotBlank();
        assertThat(catalog.questionForFlight(null)).isNotBlank();
    }

    @Test
    void fallsBackForUnknownFieldName() {
        assertThat(catalog.questionForHotel(List.of("totallyUnknownField"))).isNotBlank();
    }
}
