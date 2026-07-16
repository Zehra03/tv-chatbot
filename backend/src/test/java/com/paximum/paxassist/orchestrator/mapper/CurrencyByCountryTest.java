package com.paximum.paxassist.orchestrator.mapper;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CurrencyByCountryTest {

    @Test
    void mapsACountryToItsCurrency() {
        assertThat(CurrencyByCountry.forCountry("TR")).isEqualTo("TRY");
        assertThat(CurrencyByCountry.forCountry("DE")).isEqualTo("EUR");
        assertThat(CurrencyByCountry.forCountry("GB")).isEqualTo("GBP");
        assertThat(CurrencyByCountry.forCountry("US")).isEqualTo("USD");
    }

    @Test
    void acceptsALowercaseOrPaddedCode() {
        assertThat(CurrencyByCountry.forCountry(" de ")).isEqualTo("EUR");
    }

    @Test
    void fallsBackToTryForAMissingOrUnmappedCountry() {
        assertThat(CurrencyByCountry.forCountry(null)).isEqualTo("TRY");
        assertThat(CurrencyByCountry.forCountry("")).isEqualTo("TRY");
        assertThat(CurrencyByCountry.forCountry("ZZ")).isEqualTo("TRY");
    }

    @Test
    void anExplicitlyRequestedCurrencyWinsOverTheLocation() {
        assertThat(CurrencyByCountry.resolve("USD", "DE")).isEqualTo("USD");
    }

    @Test
    void fallsBackToTheLocationWhenNoCurrencyWasRequested() {
        assertThat(CurrencyByCountry.resolve(null, "DE")).isEqualTo("EUR");
        assertThat(CurrencyByCountry.resolve("  ", "GB")).isEqualTo("GBP");
    }
}
