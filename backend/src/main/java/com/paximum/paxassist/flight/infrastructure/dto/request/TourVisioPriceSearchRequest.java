package com.paximum.paxassist.flight.infrastructure.dto.request;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record TourVisioPriceSearchRequest(
        @JsonProperty("ProductType") int productType,
        @JsonProperty("ServiceTypes") List<String> serviceTypes,
        @JsonProperty("CheckIn") String checkIn,
        @JsonProperty("Night") Integer night,
        @JsonProperty("DepartureLocations") List<TourVisioLocationRequest> departureLocations,
        @JsonProperty("ArrivalLocations") List<TourVisioLocationRequest> arrivalLocations,
        @JsonProperty("Passengers") List<TourVisioPassengerRequest> passengers,
        @JsonProperty("Culture") String culture,
        @JsonProperty("Currency") String currency) {
}