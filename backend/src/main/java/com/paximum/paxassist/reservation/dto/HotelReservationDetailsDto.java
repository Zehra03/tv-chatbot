package com.paximum.paxassist.reservation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class HotelReservationDetailsDto {

    @NotBlank(message = "Otel adı boş bırakılamaz")
    @Size(max = 200, message = "Otel adı en fazla 200 karakter olabilir")
    private String hotelName;

    @Size(max = 150)
    private String region;

    private Short stars;

    @Size(max = 50)
    private String boardType;

    @NotNull(message = "Giriş tarihi (check-in) zorunludur")
    private LocalDate checkIn;

    @NotNull(message = "Çıkış tarihi (check-out) zorunludur")
    private LocalDate checkOut;

    @NotNull(message = "Oda sayısı zorunludur")
    @Positive(message = "Oda sayısı 0'dan büyük olmalıdır")
    private Short rooms;

    @NotNull(message = "Yetişkin sayısı zorunludur")
    @Positive(message = "Yetişkin sayısı 0'dan büyük olmalıdır")
    private Short adults;

    @NotNull(message = "Çocuk sayısı zorunludur")
    @PositiveOrZero(message = "Çocuk sayısı negatif olamaz")
    private Short children;

    @Size(min = 2, max = 2, message = "Uyruk (nationality) 2 karakterli ISO kodu olmalıdır")
    private String nationality;

    @NotNull(message = "Otel fiyatı zorunludur")
    @PositiveOrZero(message = "Fiyat negatif olamaz")
    private BigDecimal price;

    @NotBlank(message = "Para birimi zorunludur")
    @Size(min = 3, max = 3, message = "Para birimi 3 karakter olmalıdır (örn. USD, TRY)")
    private String currency;
}
