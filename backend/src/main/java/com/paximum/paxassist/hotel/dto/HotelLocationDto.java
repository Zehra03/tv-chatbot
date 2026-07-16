package com.paximum.paxassist.hotel.dto;

/**
 * One destination autocomplete suggestion for the hotel search form
 * ({@code HotelLocation} in {@code frontend/src/types/search.ts}). {@code name} is the place name the
 * dropdown shows and the frontend sends back as the search {@code destination} ("Antalya"); the hotel
 * search resolves it to a TourVisio location id again, so the name (not the id) is what travels.
 * {@code type} is {@code "city"} (the only named suggestion TourVisio hotel autocomplete exposes).
 */
public record HotelLocationDto(String id, String name, String type) {

    public static final String TYPE_CITY = "city";
}
