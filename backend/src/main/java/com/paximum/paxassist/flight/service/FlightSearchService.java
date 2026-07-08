package com.paximum.paxassist.flight.service;

import com.paximum.paxassist.flight.domain.FlightSearchCriteria;

/**
 * Flight search use-case. Implemented by {@link TourVisioFlightSearchService} (real TourVisio,
 * {@code @Profile("!mock & !demo")}) and {@link MockFlightSearchService} (static demo data,
 * {@code @Profile("mock | demo")}), mirroring the hotel module's client seam so offline/demo runs
 * return real flight cards without TourVisio credentials.
 */
public interface FlightSearchService {

    FlightSearchOutcome search(FlightSearchCriteria criteria);
}
