package com.paximum.paxassist.flight;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.List;

public record FlightSearchRequest(
        @NotBlank String origin,
        @NotBlank String destination,
        @NotNull LocalDate departureDate,
        LocalDate returnDate,
        @NotNull @Min(1) Integer adults,
        Integer children,
        List<Integer> childAges,
        String cabinClass,
        String nationality,
        String currency
) {
    // Cross-field kurallar; null alanlar @NotNull tarafından zaten yakalanır

    @JsonIgnore
    @AssertTrue(message = "returnDate, departureDate'ten önce olamaz")
    public boolean isReturnDateValid() {
        return returnDate == null || departureDate == null || !returnDate.isBefore(departureDate);
    }

    @JsonIgnore
    @AssertTrue(message = "origin ve destination aynı olamaz")
    public boolean isRouteValid() {
        return origin == null || destination == null || !origin.equalsIgnoreCase(destination);
    }

    @JsonIgnore
    @AssertTrue(message = "departureDate geçmiş bir tarih olamaz")
    public boolean isDepartureNotInPast() {
        return departureDate == null || !departureDate.isBefore(LocalDate.now());
    }
}
