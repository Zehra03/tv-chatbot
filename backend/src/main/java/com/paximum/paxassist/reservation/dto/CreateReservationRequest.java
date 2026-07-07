package com.paximum.paxassist.reservation.dto;

import com.paximum.paxassist.reservation.domain.ProductType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class CreateReservationRequest {

    @NotNull(message = "Ürün tipi (productType) zorunludur")
    private ProductType productType;

    @NotNull(message = "Toplam tutar zorunludur")
    @PositiveOrZero(message = "Toplam tutar negatif olamaz")
    private BigDecimal totalAmount;

    @NotBlank(message = "Para birimi zorunludur")
    @Size(min = 3, max = 3, message = "Para birimi 3 karakter olmalıdır")
    private String currency;

    @Size(max = 200)
    private String leadGuestName;

    @NotEmpty(message = "En az bir yolcu bilgisi gereklidir")
    @Valid
    private List<PassengerDto> passengers;

    @Valid
    private HotelReservationDetailsDto hotelDetails;

    @Valid
    private FlightReservationDetailsDto flightDetails;
}
