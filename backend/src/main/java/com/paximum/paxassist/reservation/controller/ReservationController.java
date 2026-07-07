package com.paximum.paxassist.reservation.controller;

import com.paximum.paxassist.reservation.dto.CreateReservationRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/reservations")
public class ReservationController {

    @PostMapping
    public ResponseEntity<?> createReservation(
            @Valid @RequestBody CreateReservationRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {
        
        // TICKET 5: Security / Ownership Check için kullanıcı e-postası (ID'si) alınır.
        // Bu bilgi ReservationService'e gönderilerek rezervasyonun bu kişiye ait olup olmadığı kontrol edilir.
        String userEmail = userDetails != null ? userDetails.getUsername() : "anonymous";
        
        // TODO: ReservationService entegrasyonu (iş mantığı)
        // Validasyon ve Auth kontrolünden geçerse buraya ulaşır.
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(Map.of(
                        "message", "Rezervasyon isteği geçerli ve işleniyor.", 
                        "user", userEmail,
                        "data", request));
    }
}
