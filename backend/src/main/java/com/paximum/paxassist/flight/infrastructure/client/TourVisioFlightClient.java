package com.paximum.paxassist.flight.infrastructure.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import com.paximum.paxassist.flight.infrastructure.dto.request.TourVisioPriceSearchRequest;
import com.paximum.paxassist.flight.infrastructure.dto.response.TourVisioPriceSearchResponse;

@FeignClient(
        name = "tourvisio-flight-client",
        url = "${tourvisio.url}",
        configuration = TourVisioFlightClientConfig.class)
public interface TourVisioFlightClient {

    @PostMapping("/productservice/pricesearch")
    TourVisioPriceSearchResponse priceSearch(@RequestBody TourVisioPriceSearchRequest request);
}