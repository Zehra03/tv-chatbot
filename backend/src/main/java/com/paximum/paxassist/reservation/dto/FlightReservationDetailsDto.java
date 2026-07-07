package com.paximum.paxassist.reservation.dto;

import com.paximum.paxassist.reservation.domain.TripType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Data
public class FlightReservationDetailsDto {

    @NotBlank(message = "Kalkış noktası (origin) zorunludur")
    @Size(max = 100)
    private String origin;

    @NotBlank(message = "Varış noktası (destination) zorunludur")
    @Size(max = 100)
    private String destination;

    @Size(max = 100)
    private String airline;

    @NotNull(message = "Yolculuk tipi (tripType) zorunludur")
    private TripType tripType;

    @NotNull(message = "Kalkış zamanı zorunludur")
    private OffsetDateTime departTime;

    private OffsetDateTime arriveTime;

    private OffsetDateTime returnDepartTime;

    private OffsetDateTime returnArriveTime;

    @NotNull(message = "Aktarma sayısı zorunludur")
    @PositiveOrZero(message = "Aktarma sayısı negatif olamaz")
    private Short stops;

    @Size(max = 50)
    private String baggage;

    @NotNull(message = "Yolcu sayısı zorunludur")
    @Positive(message = "Yolcu sayısı 0'dan büyük olmalıdır")
    private Short passengerCount;

    @NotNull(message = "Uçuş fiyatı zorunludur")
    @PositiveOrZero(message = "Fiyat negatif olamaz")
    private BigDecimal price;

    @NotBlank(message = "Para birimi zorunludur")
    @Size(min = 3, max = 3, message = "Para birimi 3 karakter olmalıdır (örn. USD, TRY)")
    private String currency;
}
