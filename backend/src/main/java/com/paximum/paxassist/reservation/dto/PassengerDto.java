package com.paximum.paxassist.reservation.dto;

import com.paximum.paxassist.reservation.domain.PassengerType;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class PassengerDto {

    @NotBlank(message = "Ad alanı boş bırakılamaz")
    @Size(max = 100, message = "Ad alanı en fazla 100 karakter olabilir")
    private String firstName;

    @NotBlank(message = "Soyad alanı boş bırakılamaz")
    @Size(max = 100, message = "Soyad alanı en fazla 100 karakter olabilir")
    private String lastName;

    @NotNull(message = "Yolcu tipi boş bırakılamaz")
    private PassengerType passengerType;

    private Integer age;

    @Size(min = 2, max = 2, message = "Uyruk (nationality) 2 karakterli ISO kodu olmalıdır")
    private String nationality;

    @Email(message = "Geçerli bir e-posta adresi giriniz")
    @Size(max = 254)
    private String email;

    @Size(max = 32)
    private String phone;
}
