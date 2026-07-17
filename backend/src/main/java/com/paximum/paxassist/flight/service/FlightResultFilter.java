package com.paximum.paxassist.flight.service;

import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;

import com.paximum.paxassist.flight.domain.BaggageAllowance;
import com.paximum.paxassist.flight.domain.FlightProduct;
import com.paximum.paxassist.flight.domain.FlightSearchCriteria;

/**
 * Applies the criteria's optional result filters ({@code nonstop}, {@code preferredAirline},
 * departure-time window, baggage). TourVisio's price search takes none of them as a request
 * parameter, so all are honoured here on the mapped results — without this step the flags reach the
 * criteria and are silently swallowed.
 *
 * <p>Filtering only ever removes provider results; it never invents one.
 */
final class FlightResultFilter {

    private FlightResultFilter() {
    }

    /**
     * @param zone zone the products' {@code departTime} instants are read in — the caller's own clock.
     *             The TourVisio path passes {@code tourvisio.timezone}, the zone its offset-less times
     *             were parsed with, so an "08:00–12:00" window means the time printed on the ticket
     *             rather than a UTC coincidence.
     */
    static List<FlightProduct> apply(FlightSearchCriteria criteria, List<FlightProduct> products, ZoneId zone) {
        return products.stream()
                .filter(product -> matchesNonstop(criteria, product))
                .filter(product -> matchesAirline(criteria, product))
                .filter(product -> matchesDepartWindow(criteria, product, zone))
                .filter(product -> matchesBaggage(criteria, product))
                .toList();
    }

    /**
     * A no-op for the TourVisio path, whose response mapper already priced each card at a fare that
     * meets the request — this is what enforces the same rule for search services that map no offers
     * of their own (mock/demo). A product whose allowance is unknown is dropped while baggage is
     * requested, for the same reason an unknown departure time is: it must not be presented as
     * meeting a filter that could not be evaluated against it.
     */
    private static boolean matchesBaggage(FlightSearchCriteria criteria, FlightProduct product) {
        BaggageAllowance allowance = product.getBaggageAllowance() != null
                ? product.getBaggageAllowance()
                : BaggageAllowance.unknown();
        return allowance.satisfies(criteria.getCheckedBaggage(), criteria.getMinCheckedBaggageKg());
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

    /**
     * Bounds are inclusive and independent — a window with only a {@code from} means "at or after".
     * A flight whose departure time is unknown is dropped while a window is requested: it must not be
     * presented as satisfying a filter that could not be evaluated against it.
     */
    private static boolean matchesDepartWindow(FlightSearchCriteria criteria, FlightProduct product, ZoneId zone) {
        LocalTime from = criteria.getDepartTimeFrom();
        LocalTime to = criteria.getDepartTimeTo();
        if (from == null && to == null) {
            return true;
        }
        if (product.getDepartTime() == null) {
            return false;
        }
        LocalTime departs = product.getDepartTime().atZone(zone).toLocalTime();
        if (from != null && departs.isBefore(from)) {
            return false;
        }
        return to == null || !departs.isAfter(to);
    }
}
