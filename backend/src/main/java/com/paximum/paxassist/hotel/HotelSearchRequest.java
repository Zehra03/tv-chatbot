package com.paximum.paxassist.hotel;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.List;

public record HotelSearchRequest(
        @NotBlank String location,
        @NotNull LocalDate checkIn,
        @NotNull LocalDate checkOut,
        @NotNull @Min(1) Integer adults,
        Integer children,
        List<Integer> childAges,
        @NotNull @Min(1) Integer rooms,
        Integer stars,
        String boardType,
        String nationality,
        String currency,
        String sortBy
) {
    // Cross-field kurallar; null alanlar @NotNull tarafından zaten yakalanır

    @JsonIgnore
    @AssertTrue(message = "checkOut, checkIn'den sonra olmalıdır")
    public boolean isDateRangeValid() {
        return checkIn == null || checkOut == null || checkOut.isAfter(checkIn);
    }

    @JsonIgnore
    @AssertTrue(message = "checkIn geçmiş bir tarih olamaz")
    public boolean isCheckInNotInPast() {
        return checkIn == null || !checkIn.isBefore(LocalDate.now());
    }
}
