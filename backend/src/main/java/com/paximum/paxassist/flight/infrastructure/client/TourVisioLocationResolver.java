package com.paximum.paxassist.flight.infrastructure.client;

import java.util.List;
import java.util.Optional;

import feign.FeignException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import com.paximum.paxassist.flight.config.TourVisioProperties;
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

    private Optional<String> resolve(String place, boolean departure) {
        if (place == null || place.isBlank()) {
            return Optional.empty();
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
            return Optional.empty();
        }

        List<Item> items = response.body().items();
        // Prefer an exact id/code match (user typed a real code), else fall back to the top suggestion.
        return items.stream().filter(item -> matchesExactly(item, query)).findFirst()
                .or(() -> items.stream().findFirst())
                .map(TourVisioLocationResolver::idOf)
                .filter(id -> id != null && !id.isBlank());
    }

    private TourVisioAutocompleteResponse autocomplete(boolean departure, TourVisioAutocompleteRequest request) {
        return departure
                ? flightClient.departureAutocomplete(request)
                : flightClient.arrivalAutocomplete(request);
    }

    private static boolean matchesExactly(Item item, String query) {
        if (item.city() != null && query.equalsIgnoreCase(item.city().id())) {
            return true;
        }
        return item.airport() != null
                && (query.equalsIgnoreCase(item.airport().id()) || query.equalsIgnoreCase(item.airport().code()));
    }

    private static String idOf(Item item) {
        if (item.city() != null && item.city().id() != null) {
            return item.city().id();
        }
        if (item.airport() != null && item.airport().id() != null) {
            return item.airport().id();
        }
        return null;
    }
}
