package com.paximum.paxassist.hotel.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record AutocompleteResponse(
    Header header,
    Body body
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Header(boolean success) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Body(List<Item> items) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Item(
        int type,
        City city,
        Hotel giataInfo
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record City(
        String id,
        String name
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Hotel(
        String hotelId,
        String destinationId
    ) {}
}
