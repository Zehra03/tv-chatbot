package com.paximum.paxassist.hotel.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record LoginResponse(
    Header header,
    Body body
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Header(boolean success) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Body(
        String token,
        String expiresOn
    ) {}
}
