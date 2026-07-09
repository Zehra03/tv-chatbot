package com.paximum.paxassist.flight.service;

import java.util.List;

import com.paximum.paxassist.flight.domain.FlightLocation;

/**
 * Origin/destination autocomplete use-case — turns the free text a user types ("Ant") into real
 * TourVisio locations ("Antalya", "Antalya Havalimanı (AYT)") for a search-form dropdown. Mirrors
 * the {@link FlightSearchService} seam: real TourVisio impl ({@code @Profile("!mock & !demo")}) and a
 * static-data impl ({@code @Profile("mock | demo")}) so offline/demo runs still populate the dropdown.
 */
public interface FlightLocationService {

    /**
     * @param query     free text the user typed (e.g. "Ant")
     * @param departure {@code true} for the origin field, {@code false} for the destination field
     * @return up to a handful of matching locations, never {@code null}; empty when nothing matches
     */
    List<FlightLocation> suggest(String query, boolean departure);
}
