package com.paximum.paxassist.hotel;

import com.paximum.paxassist.common.log.LogModuleClient;
import com.paximum.paxassist.hotel.dto.AutocompleteResponse;
import com.paximum.paxassist.hotel.dto.HotelSearchRequest;
import com.paximum.paxassist.hotel.dto.HotelSearchResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class HotelSearchServiceImpl implements HotelSearchService {

    private static final Logger log = LoggerFactory.getLogger(HotelSearchServiceImpl.class);
    
    private final TourVisioHotelApiClient tourVisioHotelApiClient;
    private final LogModuleClient logModuleClient;

    public HotelSearchServiceImpl(TourVisioHotelApiClient tourVisioHotelApiClient, LogModuleClient logModuleClient) {
        this.tourVisioHotelApiClient = tourVisioHotelApiClient;
        this.logModuleClient = logModuleClient;
    }

    @Override
    @Cacheable(
        value = "hotelSearch", 
        key = "#request.destination().toLowerCase() + '_' + #request.checkIn() + '_' + #request.night() + '_' + #request.adult() + '_' + #request.currency().toUpperCase()", 
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
            AutocompleteResponse autocompleteRes = tourVisioHotelApiClient.getArrivalAutocomplete(request.destination());
            if (autocompleteRes != null && autocompleteRes.body() != null && autocompleteRes.body().items() != null) {
                for (AutocompleteResponse.Item item : autocompleteRes.body().items()) {
                    if (item.city() != null && item.city().id() != null) {
                        locationId = item.city().id();
                        break;
                    }
                }
            }
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
            return HotelSearchResponse.success(null);
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
}
