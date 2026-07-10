package com.paximum.paxassist.flight.service;

import java.util.List;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import com.paximum.paxassist.flight.domain.FlightLocation;
import com.paximum.paxassist.flight.infrastructure.client.TourVisioLocationResolver;

/**
 * Real autocomplete path — delegates to {@link TourVisioLocationResolver#suggest}, the same
 * TourVisio autocomplete the search flow uses to resolve free text to a location id. Kept as a thin
 * seam so the controller depends on {@link FlightLocationService} (present in every profile) rather
 * than the resolver (which only exists under the real profile).
 */
@Service
@Profile("!mock & !demo")
public class TourVisioFlightLocationService implements FlightLocationService {

    private final TourVisioLocationResolver locationResolver;

    public TourVisioFlightLocationService(TourVisioLocationResolver locationResolver) {
        this.locationResolver = locationResolver;
    }

    @Override
    public List<FlightLocation> suggest(String query, boolean departure) {
        return locationResolver.suggest(query, departure);
    }
}
