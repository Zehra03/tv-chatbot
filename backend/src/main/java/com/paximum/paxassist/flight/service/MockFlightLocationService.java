package com.paximum.paxassist.flight.service;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import com.paximum.paxassist.flight.domain.FlightLocation;

/**
 * Static location suggestions for offline runs — active under {@code mock}/{@code demo}, so the
 * search-form dropdown works without TourVisio credentials. A small set of common TR/EU airports,
 * filtered by a diacritic-insensitive substring of name/id. The real path is
 * {@link TourVisioFlightLocationService}.
 */
@Service
@Profile("mock | demo")
public class MockFlightLocationService implements FlightLocationService {

    private static final int MAX_RESULTS = 8;

    private static final List<FlightLocation> LOCATIONS = List.of(
            new FlightLocation("IST", "IST", "İstanbul Havalimanı (IST)", FlightLocation.TYPE_AIRPORT),
            new FlightLocation("SAW", "SAW", "İstanbul Sabiha Gökçen (SAW)", FlightLocation.TYPE_AIRPORT),
            new FlightLocation("AYT", "AYT", "Antalya Havalimanı (AYT)", FlightLocation.TYPE_AIRPORT),
            new FlightLocation("ESB", "ESB", "Ankara Esenboğa (ESB)", FlightLocation.TYPE_AIRPORT),
            new FlightLocation("ADB", "ADB", "İzmir Adnan Menderes (ADB)", FlightLocation.TYPE_AIRPORT),
            new FlightLocation("DLM", "DLM", "Dalaman (DLM)", FlightLocation.TYPE_AIRPORT),
            new FlightLocation("BJV", "BJV", "Bodrum Milas (BJV)", FlightLocation.TYPE_AIRPORT),
            new FlightLocation("LHR", "LHR", "London Heathrow (LHR)", FlightLocation.TYPE_AIRPORT),
            new FlightLocation("CDG", "CDG", "Paris Charles de Gaulle (CDG)", FlightLocation.TYPE_AIRPORT),
            new FlightLocation("FRA", "FRA", "Frankfurt (FRA)", FlightLocation.TYPE_AIRPORT),
            new FlightLocation("AMS", "AMS", "Amsterdam Schiphol (AMS)", FlightLocation.TYPE_AIRPORT),
            new FlightLocation("BER", "BER", "Berlin Brandenburg (BER)", FlightLocation.TYPE_AIRPORT));

    @Override
    public List<FlightLocation> suggest(String query, boolean departure) {
        if (query == null || query.isBlank()) {
            return List.of();
        }
        String needle = fold(query);
        return LOCATIONS.stream()
                .filter(loc -> fold(loc.name()).contains(needle) || fold(loc.id()).contains(needle))
                .limit(MAX_RESULTS)
                .toList();
    }

    /** Lower-cases and strips diacritics so "istanbul" matches "İstanbul". */
    private static String fold(String value) {
        String normalized = Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "");
        return normalized.toLowerCase(Locale.ROOT).replace("ı", "i");
    }
}
