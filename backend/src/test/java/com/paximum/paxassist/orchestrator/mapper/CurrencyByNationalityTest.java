package com.paximum.paxassist.orchestrator.mapper;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CurrencyByNationalityTest {

    @Test
    void mapsANationalityToItsCurrency() {
        assertThat(CurrencyByNationality.forNationality("TR")).isEqualTo("TRY");
        assertThat(CurrencyByNationality.forNationality("DE")).isEqualTo("EUR");
        assertThat(CurrencyByNationality.forNationality("GB")).isEqualTo("GBP");
        assertThat(CurrencyByNationality.forNationality("US")).isEqualTo("USD");
    }

    @Test
    void acceptsALowercaseOrPaddedCode() {
        assertThat(CurrencyByNationality.forNationality(" de ")).isEqualTo("EUR");
    }

    @Test
    void fallsBackToTryForAMissingOrUnmappedNationality() {
        assertThat(CurrencyByNationality.forNationality(null)).isEqualTo("TRY");
        assertThat(CurrencyByNationality.forNationality("")).isEqualTo("TRY");
        assertThat(CurrencyByNationality.forNationality("ZZ")).isEqualTo("TRY");
    }

    @Test
    void anExplicitlyRequestedCurrencyWinsOverTheNationality() {
        assertThat(CurrencyByNationality.resolve("USD", "DE")).isEqualTo("USD");
    }

    @Test
    void fallsBackToTheNationalityWhenNoCurrencyWasRequested() {
        assertThat(CurrencyByNationality.resolve(null, "DE")).isEqualTo("EUR");
        assertThat(CurrencyByNationality.resolve("  ", "GB")).isEqualTo("GBP");
    }
}
