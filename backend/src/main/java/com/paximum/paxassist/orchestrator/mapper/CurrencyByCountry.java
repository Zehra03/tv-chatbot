package com.paximum.paxassist.orchestrator.mapper;

import java.util.Locale;
import java.util.Map;

/**
 * Picks the search currency from the country the traveller is browsing FROM, so Paxi never has to
 * ask for it — a question users find pointless ("Fiyatları hangi para biriminde görmek istersin?")
 * when the answer follows from where they are.
 *
 * <p>Deliberately keyed on location, not nationality. Those are different facts and only location
 * predicts how someone pays: a Turkish citizen living in Germany connects from a DE address and
 * settles in EUR, so quoting them TRY because their passport says TR would be the worse guess.
 * Nationality stays a separate, user-supplied field — it decides the provider's pricing contract
 * and lands on the reservation as a fact about a person, so no guess may fill it silently.
 *
 * <p>This is a display/search currency only: the provider prices in it, and the reservation flow
 * shows what the provider returned. An unknown or missing country falls back to {@code TRY}.
 * The guess is always overridable by the user, which is what makes guessing acceptable here at all.
 */
public final class CurrencyByCountry {

    private CurrencyByCountry() {
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
     * @param countryCode ISO-3166 alpha-2 code of where the request came from, or null
     * @return the currency to search in; {@code TRY} when the country is missing or unmapped
     */
    public static String forCountry(String countryCode) {
        if (countryCode == null || countryCode.isBlank()) {
            return DEFAULT_CURRENCY;
        }
        return CURRENCIES.getOrDefault(countryCode.strip().toUpperCase(Locale.ROOT), DEFAULT_CURRENCY);
    }

    /** The currency the user chose when they named one, otherwise the one their location suggests. */
    public static String resolve(String requestedCurrency, String countryCode) {
        if (requestedCurrency != null && !requestedCurrency.isBlank()) {
            return requestedCurrency;
        }
        return forCountry(countryCode);
    }
}
