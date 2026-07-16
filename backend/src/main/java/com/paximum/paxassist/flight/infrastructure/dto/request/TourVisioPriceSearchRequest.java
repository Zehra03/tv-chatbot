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
        /**
         * Asks for the response shape that carries each offer's group keys and per-group booking
         * tokens — what pairing a return with an outbound needs. Without it the provider answers in
         * the legacy shape, whose flat single offer id cannot express such a pairing.
         *
         * <p>The misspelling ("Reponse") is TourVisio's own: it is the wire name, so it must stay.
         */
        @JsonProperty("supportedFlightReponseListTypes") List<Integer> supportedFlightResponseListTypes,
        @JsonProperty("Culture") String culture,
        @JsonProperty("Currency") String currency) {
}