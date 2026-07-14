package com.paximum.paxassist.hotel.dto;

import java.util.List;

public record HotelSearchResponse(
    String status,
    List<String> missingParameters,
    Object results
) {
    public static HotelSearchResponse incomplete(List<String> missingParameters) {
        return new HotelSearchResponse("INCOMPLETE", missingParameters, null);
    }

    public static HotelSearchResponse success(Object results) {
        return new HotelSearchResponse("SUCCESS", List.of(), results);
    }

    public static HotelSearchResponse invalidLocation(String locationName) {
        return new HotelSearchResponse("INVALID_LOCATION", List.of(), locationName);
    }
}
