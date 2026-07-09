package com.paximum.paxassist.flight.infrastructure.client;

import java.util.List;
import java.util.Optional;

import feign.FeignException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import com.paximum.paxassist.flight.config.TourVisioProperties;
import com.paximum.paxassist.flight.domain.FlightLocation;
import com.paximum.paxassist.flight.infrastructure.dto.request.TourVisioAutocompleteRequest;
import com.paximum.paxassist.flight.infrastructure.dto.response.TourVisioAutocompleteResponse;
import com.paximum.paxassist.flight.infrastructure.dto.response.TourVisioAutocompleteResponse.Item;

/**
 * Resolves the free-text origin/destination a user types ("Antalya", "İstanbul") into the
 * location ids TourVisio's flight price search requires ("AYT", "IST"). Price search matches
 * {@code DepartureLocations}/{@code ArrivalLocations} on id only — raw place names return nothing,
 * which surfaced as a 502 "temporarily unavailable" on the frontend. This mirrors the hotel
 * module's autocomplete step ({@code TourVisioHotelApiClientImpl#searchHotels}).
 *
 * <p>When the user already typed a valid code the fuzzy autocomplete may rank an unrelated place
 * first (query {@code "AYT"} lists Puerto Ayacucho/PYH ahead of Antalya/AYT), so an exact id/code
 * match wins over the top suggestion. An unresolvable place yields {@link Optional#empty()} — the
 * caller degrades to an empty result rather than an error.
 */
@Component
@Profile("!mock & !demo")
public class TourVisioLocationResolver {

    private static final Logger log = LoggerFactory.getLogger(TourVisioLocationResolver.class);
    private static final int PRODUCT_TYPE_FLIGHT = 3;

    private final TourVisioFlightClient flightClient;
    private final TourVisioProperties tourVisioProperties;
    private final TourVisioTokenProvider tokenProvider;

    public TourVisioLocationResolver(
            TourVisioFlightClient flightClient,
            TourVisioProperties tourVisioProperties,
            TourVisioTokenProvider tokenProvider) {
        this.flightClient = flightClient;
        this.tourVisioProperties = tourVisioProperties;
        this.tokenProvider = tokenProvider;
    }

    public Optional<String> resolveDeparture(String place) {
        return resolve(place, true);
    }

    public Optional<String> resolveArrival(String place) {
        return resolve(place, false);
    }

    /**
     * Returns the location suggestions TourVisio offers for a free-text {@code place}, mapped to the
     * shape the UI dropdown needs. Same autocomplete call {@link #resolve} uses; an empty/blank query
     * or no suggestions yields an empty list (never an error) so the caller can degrade gracefully.
     */
    public List<FlightLocation> suggest(String place, boolean departure) {
        if (place == null || place.isBlank()) {
            return List.of();
        }
        String query = place.trim();
        TourVisioAutocompleteRequest request =
                new TourVisioAutocompleteRequest(PRODUCT_TYPE_FLIGHT, query, tourVisioProperties.culture());

        TourVisioAutocompleteResponse response;
        try {
            response = autocomplete(departure, request);
        } catch (FeignException.Unauthorized e) {
            log.warn("TourVisio rejected the cached token during autocomplete; re-logging in and retrying once");
            tokenProvider.invalidate();
            response = autocomplete(departure, request);
        }

        if (response == null || response.body() == null || response.body().items() == null
                || response.body().items().isEmpty()) {
            return List.of();
        }

        return response.body().items().stream()
                .map(TourVisioLocationResolver::toLocation)
                .filter(loc -> loc != null && loc.id() != null && !loc.id().isBlank())
                .toList();
    }

    private Optional<String> resolve(String place, boolean departure) {
        List<FlightLocation> suggestions = suggest(place, departure);
        if (suggestions.isEmpty()) {
            return Optional.empty();
        }
        String query = place.trim();
        // Prefer an exact id/code match (user typed a real code), else fall back to the top suggestion.
        return suggestions.stream().filter(loc -> matchesExactly(loc, query)).findFirst()
                .or(() -> suggestions.stream().findFirst())
                .map(FlightLocation::id);
    }

    private TourVisioAutocompleteResponse autocomplete(boolean departure, TourVisioAutocompleteRequest request) {
        return departure
                ? flightClient.departureAutocomplete(request)
                : flightClient.arrivalAutocomplete(request);
    }

    private static boolean matchesExactly(FlightLocation loc, String query) {
        return query.equalsIgnoreCase(loc.id()) || query.equalsIgnoreCase(loc.code());
    }

    private static FlightLocation toLocation(Item item) {
        if (item.city() != null && item.city().id() != null) {
            return new FlightLocation(item.city().id(), null, item.city().name(), FlightLocation.TYPE_CITY);
        }
        if (item.airport() != null && item.airport().id() != null) {
            // TourVisio's airport name already carries the code (e.g. "Antalya ... (AYT)"), so use it
            // verbatim — appending the code here would double it. The UI shows code() separately.
            return new FlightLocation(item.airport().id(), item.airport().code(),
                    item.airport().name(), FlightLocation.TYPE_AIRPORT);
        }
        return null;
    }
}
