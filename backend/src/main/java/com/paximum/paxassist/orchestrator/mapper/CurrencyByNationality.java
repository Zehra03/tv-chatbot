package com.paximum.paxassist.orchestrator.mapper;

import java.util.Locale;
import java.util.Map;

/**
 * Picks the search currency from the traveller's nationality, so Paxi never has to ask for it —
 * a question users find pointless ("Fiyatları hangi para biriminde görmek istersin?") when the
 * answer is obvious from who they are.
 *
 * <p>This is a display/search currency only: the provider prices in it, and the reservation flow
 * shows what the provider returned. An unknown or missing nationality falls back to {@code TRY},
 * matching the {@code TR} nationality default the search requests already apply.
 */
public final class CurrencyByNationality {

    private CurrencyByNationality() {
    }

    static final String DEFAULT_CURRENCY = "TRY";

    private static final Map<String, String> CURRENCIES = Map.ofEntries(
            Map.entry("TR", "TRY"),
            Map.entry("US", "USD"),
            Map.entry("GB", "GBP"),
            Map.entry("CH", "CHF"),
            Map.entry("RU", "RUB"),
            Map.entry("UA", "UAH"),
            Map.entry("AZ", "AZN"),
            Map.entry("SE", "SEK"),
            Map.entry("NO", "NOK"),
            Map.entry("DK", "DKK"),
            Map.entry("PL", "PLN"),
            Map.entry("CZ", "CZK"),
            Map.entry("HU", "HUF"),
            Map.entry("RO", "RON"),
            Map.entry("BG", "BGN"),
            Map.entry("AE", "AED"),
            Map.entry("SA", "SAR"),
            Map.entry("QA", "QAR"),
            Map.entry("KW", "KWD"),
            Map.entry("IL", "ILS"),
            Map.entry("EG", "EGP"),
            Map.entry("JP", "JPY"),
            Map.entry("CN", "CNY"),
            Map.entry("IN", "INR"),
            Map.entry("CA", "CAD"),
            Map.entry("AU", "AUD"),
            // Euro area — listed explicitly rather than inferred, so an added country is a decision.
            Map.entry("DE", "EUR"), Map.entry("FR", "EUR"), Map.entry("IT", "EUR"),
            Map.entry("ES", "EUR"), Map.entry("NL", "EUR"), Map.entry("BE", "EUR"),
            Map.entry("AT", "EUR"), Map.entry("PT", "EUR"), Map.entry("GR", "EUR"),
            Map.entry("IE", "EUR"), Map.entry("FI", "EUR"), Map.entry("SK", "EUR"),
            Map.entry("SI", "EUR"), Map.entry("EE", "EUR"), Map.entry("LV", "EUR"),
            Map.entry("LT", "EUR"), Map.entry("LU", "EUR"), Map.entry("CY", "EUR"),
            Map.entry("MT", "EUR"), Map.entry("HR", "EUR"));

    /**
     * @param nationality ISO-3166 alpha-2 country code, or null
     * @return the currency to search in; {@code TRY} when the nationality is missing or unmapped
     */
    public static String forNationality(String nationality) {
        if (nationality == null || nationality.isBlank()) {
            return DEFAULT_CURRENCY;
        }
        return CURRENCIES.getOrDefault(nationality.strip().toUpperCase(Locale.ROOT), DEFAULT_CURRENCY);
    }

    /** The explicitly requested currency when the user named one, otherwise the nationality's. */
    public static String resolve(String requestedCurrency, String nationality) {
        if (requestedCurrency != null && !requestedCurrency.isBlank()) {
            return requestedCurrency;
        }
        return forNationality(nationality);
    }
}
