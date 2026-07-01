package com.paximum.paxassist.flight.infrastructure.dto.request;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record TourVisioPriceSearchRequest(
        int productType,
        List<String> serviceTypes,
        String checkIn,
        Integer night,
        List<TourVisioLocationRequest> departureLocations,
        List<TourVisioLocationRequest> arrivalLocations,
        List<TourVisioPassengerRequest> passengers,
        String culture,
        String currency) {
}