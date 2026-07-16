package com.paximum.paxassist.flight.dto;

import com.paximum.paxassist.flight.domain.FlightLocation;

/**
 * One origin/destination autocomplete suggestion in the frontend's shape
 * ({@code FlightLocation} in {@code frontend/src/types/search.ts}). {@code id} is what the frontend
 * sends back as the search {@code origin}/{@code destination}; {@code name} is what the dropdown shows.
 */
public record FlightLocationDto(String id, String code, String name, String type) {

    public static FlightLocationDto from(FlightLocation location) {
        return new FlightLocationDto(location.id(), location.code(), location.name(), location.type());
    }
}
