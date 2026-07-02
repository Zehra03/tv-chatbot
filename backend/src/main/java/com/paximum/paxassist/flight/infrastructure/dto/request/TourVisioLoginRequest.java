package com.paximum.paxassist.flight.infrastructure.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;

public record TourVisioLoginRequest(
        @JsonProperty("Agency") String agency,
        @JsonProperty("User") String user,
        @JsonProperty("Password") String password) {
}
