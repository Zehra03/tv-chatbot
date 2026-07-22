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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.paximum.paxassist.admin.dto.DashboardStatsResponse;
import com.paximum.paxassist.admin.dto.UserAdminDto;
import com.paximum.paxassist.auth.repository.UserRepository;
import com.paximum.paxassist.reservation.domain.ProductType;
import com.paximum.paxassist.reservation.domain.ReservationStatus;
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

        List<Object[]> revenueData = reservationRepository.sumRevenueByCurrency(ReservationStatus.CONFIRMED);
        Map<String, BigDecimal> revenueMap = new HashMap<>();
        for (Object[] row : revenueData) {
            String currency = (String) row[0];
            BigDecimal amount = (BigDecimal) row[1];
            if (currency != null && amount != null) {
                revenueMap.put(currency, amount);
            }
        }

        // Keyed by the enum's lowercase JSON form so the map lines up with the productType values
        // the frontend already uses everywhere else ("hotel" / "flight" / "combined").
        Map<String, Long> byProductType = new HashMap<>();
        for (Object[] row : reservationRepository.countByProductType()) {
            ProductType type = (ProductType) row[0];
            Long count = (Long) row[1];
            if (type != null && count != null) {
                byProductType.put(type.toJson(), count);
            }
        }

        return new DashboardStatsResponse(totalReservations, totalUsers, revenueMap, byProductType);
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

    /**
     * Admin reservation list, optionally filtered by PNR text, status and product type. All three
     * are optional; a blank {@code q} counts as absent so an emptied search box does not become a
     * "contains empty string" filter.
     *
     * <p>Status/product type are taken as Strings and parsed here rather than bound straight to the
     * enums: Spring binds request-param enums with {@code Enum.valueOf}, which is case-sensitive and
     * ignores the {@code @JsonCreator} these two carry — so {@code ?status=cancelled}, the exact
     * spelling used everywhere else on the wire, would 400. Parsing through {@code fromJson} keeps
     * one casing convention across body and query string.
     */
    @GetMapping("/reservations")
    public Page<ReservationSummaryResponse> listReservations(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String productType,
            Pageable pageable) {
        String pnr = (q == null || q.isBlank()) ? null : q.trim();
        return reservationRepository
                .searchForAdmin(pnr, parseStatus(status), parseProductType(productType), pageable)
                .map(reservationMapper::toSummary);
    }

    private static ReservationStatus parseStatus(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            return ReservationStatus.fromJson(raw);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Geçersiz rezervasyon durumu: " + raw);
        }
    }

    private static ProductType parseProductType(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            return ProductType.fromJson(raw);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Geçersiz ürün tipi: " + raw);
        }
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
            case CancelResult.TourVisioUnavailable ignored ->
                    ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(new OutcomeResponse("TOURVISIO_UNAVAILABLE", "Sağlayıcıya ulaşılamıyor."));
            case CancelResult.OutcomeUnknown ignored ->
                    ResponseEntity.status(HttpStatus.ACCEPTED).body(new OutcomeResponse("CANCEL_OUTCOME_UNKNOWN", "İptal talebi alındı ancak sonucu belirsiz."));
        };
    }
}
