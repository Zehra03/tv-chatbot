package com.paximum.paxassist.flight.infrastructure.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import com.paximum.paxassist.flight.infrastructure.dto.request.TourVisioAutocompleteRequest;
import com.paximum.paxassist.flight.infrastructure.dto.request.TourVisioPriceSearchRequest;
import com.paximum.paxassist.flight.infrastructure.dto.response.TourVisioAutocompleteResponse;
import com.paximum.paxassist.flight.infrastructure.dto.response.TourVisioPriceSearchResponse;

@FeignClient(
        name = "tourvisio-flight-client",
        url = "${tourvisio.url}",
        // TourVisio method paths are under /api (see API docs: /api/productservice/pricesearch).
        // The path prefix keeps tourvisio.url as the host+version base (.../v2), matching the hotel
        // client which appends /api itself — so both modules agree on the same base value.
        path = "/api",
        configuration = TourVisioFlightClientConfig.class)
public interface TourVisioFlightClient {

    @PostMapping("/productservice/pricesearch")
    TourVisioPriceSearchResponse priceSearch(@RequestBody TourVisioPriceSearchRequest request);

    // Resolve a free-text departure place ("Antalya") to a location id ("AYT"). Price search rejects
    // raw place names, so the id from here is what DepartureLocations must carry.
    @PostMapping("/productservice/getdepartureautocomplete")
    TourVisioAutocompleteResponse departureAutocomplete(@RequestBody TourVisioAutocompleteRequest request);

    // Arrival counterpart of departureAutocomplete; feeds ArrivalLocations.
    @PostMapping("/productservice/getarrivalautocomplete")
    TourVisioAutocompleteResponse arrivalAutocomplete(@RequestBody TourVisioAutocompleteRequest request);
}