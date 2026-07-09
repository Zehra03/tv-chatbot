package com.paximum.paxassist.flight.domain;

/**
 * A location suggestion for the origin/destination autocomplete — the free-text-to-id bridge the
 * flight search already relies on ({@code TourVisioLocationResolver}), now surfaced to the UI so the
 * user picks a real TourVisio location instead of typing a raw place name.
 *
 * <p>{@code id} is what the flight price search accepts as a {@code Departure}/{@code ArrivalLocation}
 * (e.g. {@code "AYT"}); {@code code} is the IATA-style airport code when the suggestion is an airport
 * (null for a city); {@code name} is a human-readable label to show ("Antalya", "Antalya Havalimanı
 * (AYT)"); {@code type} is {@code "city"} or {@code "airport"}.
 */
public record FlightLocation(String id, String code, String name, String type) {

    public static final String TYPE_CITY = "city";
    public static final String TYPE_AIRPORT = "airport";
}
