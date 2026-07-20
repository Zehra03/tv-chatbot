package com.paximum.paxassist.hotel;

import com.paximum.paxassist.common.log.LogModuleClient;
import com.paximum.paxassist.hotel.dto.AutocompleteResponse;
import com.paximum.paxassist.hotel.dto.HotelLocationDto;
import com.paximum.paxassist.hotel.dto.HotelSearchRequest;
import com.paximum.paxassist.hotel.dto.HotelSearchResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.text.Normalizer;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class HotelSearchServiceImpl implements HotelSearchService {

    private static final Logger log = LoggerFactory.getLogger(HotelSearchServiceImpl.class);

    /** Cap on destination suggestions — TourVisio hotel autocomplete can return dozens. */
    private static final int MAX_SUGGESTIONS = 12;
    
    private final TourVisioHotelApiClient tourVisioHotelApiClient;
    private final LogModuleClient logModuleClient;

    public HotelSearchServiceImpl(TourVisioHotelApiClient tourVisioHotelApiClient, LogModuleClient logModuleClient) {
        this.tourVisioHotelApiClient = tourVisioHotelApiClient;
        this.logModuleClient = logModuleClient;
    }

    @Override
    @Cacheable(
        value = "hotelSearch",
        // The key covers every field TourVisio prices on — see HotelSearchRequest#cacheKey().
        key = "#request.cacheKey()",
        unless = "#result.status() == 'INCOMPLETE'"
    )
    public HotelSearchResponse searchHotels(HotelSearchRequest request) {
        log.info("Processing hotel search request: {}", request);
        
        // 1. Programmatic validation for missing parameters
        List<String> missingParameters = new ArrayList<>();
        if (request.destination() == null || request.destination().isBlank()) {
            missingParameters.add("destination");
        }
        if (request.checkIn() == null || request.checkIn().isBlank()) {
            missingParameters.add("checkIn");
        }
        if (request.night() == null) {
            missingParameters.add("night");
        }
        if (request.adult() == null) {
            missingParameters.add("adult");
        }

        if (!missingParameters.isEmpty()) {
            log.warn("Search request is incomplete. Missing parameters: {}", missingParameters);
            logModuleClient.logActivity(
                "HotelSearchModule",
                "searchHotels",
                request.toString(),
                "INCOMPLETE",
                "Request is missing parameters: " + missingParameters
            );
            return HotelSearchResponse.incomplete(missingParameters);
        }

        // 2. Fetch destination/location autocomplete
        String locationId = null;
        try {
            locationId = firstCityId(tourVisioHotelApiClient.getArrivalAutocomplete(request.destination()), request.destination());
        } catch (Exception e) {
            log.error("Failed to autocomplete destination: {}", e.getMessage());
            logModuleClient.logActivity(
                "HotelSearchModule",
                "searchHotels",
                request.toString(),
                "FAILURE",
                "Autocomplete failed: " + e.getMessage()
            );
            throw new RuntimeException("Destination autocomplete failed: " + e.getMessage(), e);
        }

        if (locationId == null) {
            log.warn("Could not find a valid location ID for destination: {}", request.destination());
            logModuleClient.logActivity(
                "HotelSearchModule",
                "searchHotels",
                request.toString(),
                "SUCCESS",
                "No location ID found for destination " + request.destination()
            );
            return HotelSearchResponse.invalidLocation(request.destination());
        }

        // 3. Perform the actual Price Search
        try {
            log.info("Found location ID {} for {}. Querying PriceSearch...", locationId, request.destination());
            Object searchResult = tourVisioHotelApiClient.priceSearch(request, locationId);
            
            logModuleClient.logActivity(
                "HotelSearchModule",
                "searchHotels",
                request.toString(),
                "SUCCESS",
                "Hotel search completed successfully with location ID " + locationId
            );
            
            return HotelSearchResponse.success(searchResult);
        } catch (Exception e) {
            log.error("TourVisio price search failed: {}", e.getMessage());
            logModuleClient.logActivity(
                "HotelSearchModule",
                "searchHotels",
                request.toString(),
                "FAILURE",
                "PriceSearch failed: " + e.getMessage()
            );
            throw new RuntimeException("TourVisio price search failed: " + e.getMessage(), e);
        }
    }

    @Override
    public List<String> suggestAvailableCheckInDates(HotelSearchRequest base, int stepDays, int windowDays, int maxResults) {
        // Not enough context to probe (must have a resolvable destination + the search essentials).
        if (base == null || base.destination() == null || base.destination().isBlank()
                || base.night() == null || base.adult() == null || stepDays <= 0 || maxResults <= 0) {
            return List.of();
        }

        // Resolve the location ONCE, then reuse the id across every candidate date — avoids one
        // autocomplete round-trip per probe. A failure here just means "no suggestions" (never a 500).
        String locationId;
        try {
            locationId = firstCityId(tourVisioHotelApiClient.getArrivalAutocomplete(base.destination()), base.destination());
        } catch (Exception e) {
            log.warn("Date suggestion autocomplete failed for {}: {}", base.destination(), e.getMessage());
            return List.of();
        }
        if (locationId == null) {
            log.info("No location id for {} — cannot suggest alternative dates", base.destination());
            return List.of();
        }

        LocalDate anchor = parseCheckIn(base.checkIn());
        LocalDate today = LocalDate.now();
        List<String> available = new ArrayList<>();

        for (int offset = stepDays; offset <= windowDays && available.size() < maxResults; offset += stepDays) {
            LocalDate candidate = anchor.plusDays(offset);
            if (!candidate.isAfter(today)) {
                continue; // never suggest a past/today date
            }
            String checkIn = candidate.toString();
            HotelSearchRequest probe = new HotelSearchRequest(
                    base.destination(), checkIn, base.night(), base.adult(),
                    base.childAges(), base.nationality(), base.currency(), base.culture());
            try {
                if (hasResults(tourVisioHotelApiClient.priceSearch(probe, locationId))) {
                    available.add(checkIn);
                }
            } catch (Exception e) {
                log.warn("Date probe failed for check-in {}: {}", checkIn, e.getMessage());
            }
        }
        return available;
    }

    @Override
    public List<HotelLocationDto> suggestLocations(String query) {
        if (query == null || query.isBlank()) {
            return List.of();
        }
        AutocompleteResponse response;
        try {
            response = tourVisioHotelApiClient.getArrivalAutocomplete(query.trim());
        } catch (Exception e) {
            log.warn("Hotel location autocomplete failed for '{}': {}", query, e.getMessage());
            return List.of();
        }
        if (response == null || response.body() == null || response.body().items() == null) {
            return List.of();
        }
        // Only named city suggestions are useful for a place dropdown; hotel items carry no name here.
        // Dedupe by id (autocomplete can list a city alongside its hotels) while preserving order.
        Map<String, HotelLocationDto> byId = new LinkedHashMap<>();
        for (AutocompleteResponse.Item item : response.body().items()) {
            AutocompleteResponse.City city = item.city();
            if (city != null && city.id() != null && city.name() != null && !city.name().isBlank()) {
                byId.putIfAbsent(city.id(),
                        new HotelLocationDto(city.id(), decodeEntities(city.name()), HotelLocationDto.TYPE_CITY));
            }
        }
        // TourVisio hotel autocomplete matches loosely (a substring anywhere) and returns dozens
        // unranked — "Anta" buries Antalya below Costa de Cantabria. Float names that START with the
        // query to the top (stable sort keeps provider order within a tier) and cap the list so the
        // dropdown stays usable.
        String needle = fold(query.trim());
        return byId.values().stream()
                .filter(loc -> fold(loc.name()).contains(needle) || needle.contains(fold(loc.name())))
                .sorted(Comparator.comparingInt(loc -> fold(loc.name()).startsWith(needle) ? 0 : 1))
                .limit(MAX_SUGGESTIONS)
                .toList();
    }

    /** Lower-cases and strips diacritics so "istanbul" ranks against "İstanbul". */
    private static String fold(String value) {
        String normalized = Normalizer.normalize(value, Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
        return normalized.toLowerCase(Locale.ROOT).replace("ı", "i");
    }

    /** TourVisio city names arrive HTML-escaped ("L&#39;Estartit"); decode the common entities. */
    private static String decodeEntities(String value) {
        return value
                .replace("&#39;", "'")
                .replace("&amp;", "&")
                .replace("&quot;", "\"")
                .replace("&lt;", "<")
                .replace("&gt;", ">");
    }

    /** First city id from a TourVisio autocomplete response that matches the query, or null when none is present. */
    private static String firstCityId(AutocompleteResponse response, String query) {
        if (response != null && response.body() != null && response.body().items() != null && query != null && !query.isBlank()) {
            String needle = fold(query.trim());
            for (AutocompleteResponse.Item item : response.body().items()) {
                if (item.city() != null && item.city().id() != null && item.city().name() != null) {
                    String cityName = fold(item.city().name());
                    if (cityName.contains(needle) || needle.contains(cityName)) {
                        return item.city().id();
                    }
                }
            }
        }
        return null;
    }

    /** priceSearch maps to a List of HotelProduct cards — availability means at least one card. */
    private static boolean hasResults(Object priceSearchResult) {
        return priceSearchResult instanceof List<?> list && !list.isEmpty();
    }

    /** Anchor date to step forward from: the requested check-in, or tomorrow when absent/unparseable. */
    private static LocalDate parseCheckIn(String checkIn) {
        if (checkIn != null && !checkIn.isBlank()) {
            try {
                return LocalDate.parse(checkIn.trim());
            } catch (DateTimeParseException ignored) {
                // fall through to tomorrow
            }
        }
        return LocalDate.now().plusDays(1);
    }
}
