package com.paximum.paxassist.flight.infrastructure.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import com.paximum.paxassist.flight.infrastructure.dto.request.TourVisioPriceSearchRequest;
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
}