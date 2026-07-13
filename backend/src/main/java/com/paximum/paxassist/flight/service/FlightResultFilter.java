package com.paximum.paxassist.flight.service;

import java.util.List;
import java.util.Locale;

import com.paximum.paxassist.flight.domain.FlightProduct;
import com.paximum.paxassist.flight.domain.FlightSearchCriteria;

/**
 * Applies the criteria's optional result filters ({@code nonstop}, {@code preferredAirline}).
 * TourVisio's price search takes neither as a request parameter, so both are honoured here on the
 * mapped results — without this step the flags reach the criteria and are silently swallowed.
 *
 * <p>Filtering only ever removes provider results; it never invents one.
 */
final class FlightResultFilter {

    private FlightResultFilter() {
    }

    static List<FlightProduct> apply(FlightSearchCriteria criteria, List<FlightProduct> products) {
        return products.stream()
                .filter(product -> matchesNonstop(criteria, product))
                .filter(product -> matchesAirline(criteria, product))
                .toList();
    }

    private static boolean matchesNonstop(FlightSearchCriteria criteria, FlightProduct product) {
        return !Boolean.TRUE.equals(criteria.getNonstop()) || product.getStops() == 0;
    }

    /**
     * The airline arrives as an IATA code from TourVisio ("TK") but as a display name from the
     * mock/demo data ("Turkish Airlines"), and a caller may send either — so match on containment
     * in both directions. {@link Locale#ROOT} is required: under the tr-TR default locale
     * {@code "I".toLowerCase()} yields "ı" and a code like "TK" would stop matching.
     */
    private static boolean matchesAirline(FlightSearchCriteria criteria, FlightProduct product) {
        String requested = criteria.getPreferredAirline();
        if (requested == null || requested.isBlank()) {
            return true;
        }
        if (product.getAirline() == null) {
            return false;
        }
        String needle = requested.trim().toLowerCase(Locale.ROOT);
        String actual = product.getAirline().trim().toLowerCase(Locale.ROOT);
        return actual.contains(needle) || needle.contains(actual);
    }
}
