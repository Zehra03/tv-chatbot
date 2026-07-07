package com.paximum.paxassist.reservation.controller;

import com.paximum.paxassist.reservation.dto.CreateReservationRequest;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;

import com.paximum.paxassist.reservation.service.ReservationService;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/v1/reservations")
public class ReservationController {

    private final ReservationService reservationService;

    public ReservationController(ReservationService reservationService) {
        this.reservationService = reservationService;
    }

    @PostMapping
    public ResponseEntity<?> createReservation(
            @Valid @RequestBody CreateReservationRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {
        
        // TICKET 5: Security / Ownership Check için kullanıcı e-postası (ID'si) alınır.
        String userEmail = userDetails != null ? userDetails.getUsername() : "anonymous";
        
        log.info("Yeni rezervasyon isteği alındı, User: {}", userEmail);
        
        // İş mantığı ve asenkron loglama işlemi servis üzerinden gerçekleştirilir.
        Map<String, Object> response = reservationService.processReservation(request, userEmail);

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
