package com.paximum.paxassist.hotel.dto;

import java.util.List;

public record HotelSearchRequest(
    String destination,
    String checkIn,
    Integer night,
    Integer adult,
    List<Integer> childAges,
    String nationality,
    String currency,
    String culture
) {
    public HotelSearchRequest {
        if (nationality == null) {
            nationality = "TR";
        }
        if (currency == null) {
            currency = "TRY";
        }
        if (culture == null) {
            culture = "tr-TR";
        }
        if (childAges == null) {
            childAges = List.of();
        }
    }
}
