package com.paximum.paxassist.admin.controller;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.paximum.paxassist.admin.dto.DashboardStatsResponse;
import com.paximum.paxassist.admin.dto.UserAdminDto;
import com.paximum.paxassist.auth.repository.UserRepository;
import com.paximum.paxassist.reservation.repository.ReservationRepository;
import com.paximum.paxassist.reservation.service.CancelResult;
import com.paximum.paxassist.reservation.service.ReservationService;
import com.paximum.paxassist.reservation.web.ReservationWebMapper;
import com.paximum.paxassist.reservation.web.dto.CancelRequest;
import com.paximum.paxassist.reservation.web.dto.OutcomeResponse;
import com.paximum.paxassist.reservation.web.dto.ReservationSummaryResponse;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/admin")
public class AdminController {

    private final UserRepository userRepository;
    private final ReservationRepository reservationRepository;
    private final ReservationService reservationService;
    private final ReservationWebMapper reservationMapper;

    public AdminController(UserRepository userRepository,
                           ReservationRepository reservationRepository,
                           ReservationService reservationService,
                           ReservationWebMapper reservationMapper) {
        this.userRepository = userRepository;
        this.reservationRepository = reservationRepository;
        this.reservationService = reservationService;
        this.reservationMapper = reservationMapper;
    }

    @GetMapping("/dashboard/stats")
    public DashboardStatsResponse getDashboardStats() {
        long totalUsers = userRepository.count();
        long totalReservations = reservationRepository.count();

        List<Object[]> revenueData = reservationRepository.sumRevenueByCurrency(com.paximum.paxassist.reservation.domain.ReservationStatus.CONFIRMED);
        Map<String, BigDecimal> revenueMap = new HashMap<>();
        for (Object[] row : revenueData) {
            String currency = (String) row[0];
            BigDecimal amount = (BigDecimal) row[1];
            if (currency != null && amount != null) {
                revenueMap.put(currency, amount);
            }
        }

        return new DashboardStatsResponse(totalReservations, totalUsers, revenueMap);
    }

    @GetMapping("/users")
    public Page<UserAdminDto> listUsers(Pageable pageable) {
        return userRepository.findAll(pageable)
                .map(u -> new UserAdminDto(
                        u.getId(),
                        u.getEmail(),
                        u.getDisplayName(),
                        u.getRole().name(),
                        u.getCreatedAt()
                ));
    }

    @GetMapping("/reservations")
    public Page<ReservationSummaryResponse> listReservations(Pageable pageable) {
        return reservationRepository.findAll(pageable)
                .map(reservationMapper::toSummary);
    }

    @PutMapping("/reservations/{id}/status")
    public ResponseEntity<?> cancelReservation(@PathVariable Long id, @Valid @RequestBody CancelRequest request) {
        CancelResult result = reservationService.adminCancelReservation(id, request.reason(), request.serviceIds());
        return toCancelResponse(id, result);
    }

    private ResponseEntity<?> toCancelResponse(Long id, CancelResult result) {
        return switch (result) {
            case CancelResult.Cancelled ignored ->
                    ResponseEntity.ok(new OutcomeResponse("CANCELLED", "Rezervasyon iptal edildi."));
            case CancelResult.NotFound ignored -> ResponseEntity.notFound().build();
            case CancelResult.NotCancellable ignored ->
                    ResponseEntity.status(HttpStatus.CONFLICT).body(new OutcomeResponse("NOT_CANCELLABLE", "Bu rezervasyon iptal edilemiyor."));
            case CancelResult.TourVisioRejected ignored ->
                    ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(new OutcomeResponse("TOURVISIO_REJECTED", "İptal talebi sağlayıcı tarafından reddedildi."));
            // 202: the sale is still settling right after booking; the provider won't accept the void yet.
            // Nothing changed and it is retryable.
            case CancelResult.NotReadyYet ignored ->
                    ResponseEntity.status(HttpStatus.ACCEPTED).body(new OutcomeResponse("CANCEL_NOT_READY",
                            "Rezervasyon yeni oluşturulduğu için iptal şu an yapılamıyor; işlem tamamlanınca birkaç dakika içinde tekrar deneyin."));
            case CancelResult.TourVisioUnavailable ignored ->
                    ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(new OutcomeResponse("TOURVISIO_UNAVAILABLE", "Sağlayıcıya ulaşılamıyor."));
            case CancelResult.OutcomeUnknown ignored ->
                    ResponseEntity.status(HttpStatus.ACCEPTED).body(new OutcomeResponse("CANCEL_OUTCOME_UNKNOWN", "İptal talebi alındı ancak sonucu belirsiz."));
        };
    }
}
