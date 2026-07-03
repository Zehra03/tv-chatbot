package com.paximum.paxassist.hotel.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record LoginRequest(
    @JsonProperty("Agency") String agency,
    @JsonProperty("User") String user,
    @JsonProperty("Password") String password
) {}
