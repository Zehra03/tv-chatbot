package com.paximum.paxassist.reservation.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.paximum.paxassist.auth.security.UserPrincipal;
import com.paximum.paxassist.reservation.recovery.OrphanedBooking;
import com.paximum.paxassist.reservation.recovery.OrphanedBookingService;
import com.paximum.paxassist.reservation.web.dto.OrphanedBookingResponse;
import com.paximum.paxassist.reservation.web.dto.ReconcileOrphanedBookingRequest;

import jakarta.validation.Valid;

/**
 * Admin-only recovery surface for {@link com.paximum.paxassist.reservation.recovery.OrphanedBooking}.
 *
 * <p>An orphaned booking is a TourVisio purchase that committed (a real charge exists) but whose
 * local {@code Reservation} write then failed — {@code ReservationService#handleOrphanedBooking}
 * captures it durably, but until this controller nothing ever read it back: no endpoint, no scheduled
 * job. A customer could be charged with no reservation ever appearing in "Rezervasyonlarım", and the
 * only way to notice was to query the {@code orphaned_bookings} table by hand.
 *
 * <p>Deliberately its own controller, separate from {@link ReservationController} — this is a
 * support/ops tool over a standalone recovery table, not part of the customer-facing booking contract.
 *
 * <p>Gated with {@code @PreAuthorize} (method security, {@code @EnableMethodSecurity} in
 * {@code SecurityConfig}) rather than a {@code SecurityConfig} path matcher: the general
 * {@code /api/v1/reservations/**} rule already allows plain {@code ROLE_USER}, and a path-based
 * override for this one sub-path would have to sit in the same spot in {@code SecurityConfig} as the
 * not-yet-merged admin-panel branch's own {@code /api/v1/admin/**} matcher — confirmed via a trial
 * merge to conflict there. An annotation here needs no shared edit to that file at all.
 */
@RestController
@RequestMapping("/api/v1/reservations/orphaned")
@PreAuthorize("hasRole('ADMIN')")
public class OrphanedBookingController {

    private final OrphanedBookingService orphanedBookingService;

    public OrphanedBookingController(OrphanedBookingService orphanedBookingService) {
        this.orphanedBookingService = orphanedBookingService;
    }

    /**
     * GET /api/v1/reservations/orphaned?includeReconciled=false (default) — the operator queue,
     * oldest-first. {@code includeReconciled=true} additionally shows already-resolved entries
     * (newest first) for auditing.
     */
    @GetMapping
    public ResponseEntity<List<OrphanedBookingResponse>> list(
            @RequestParam(name = "includeReconciled", defaultValue = "false") boolean includeReconciled) {
        List<OrphanedBooking> bookings = includeReconciled
                ? orphanedBookingService.listAll()
                : orphanedBookingService.listUnreconciled();
        return ResponseEntity.ok(bookings.stream().map(OrphanedBookingResponse::from).toList());
    }

    /**
     * PATCH /api/v1/reservations/orphaned/{id}/reconcile — marks an entry reconciled once an operator
     * has manually verified or recreated the reservation. {@code note} should record how.
     */
    @PatchMapping("/{id}/reconcile")
    public ResponseEntity<OrphanedBookingResponse> reconcile(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody(required = false) ReconcileOrphanedBookingRequest request) {
        String note = request == null ? null : request.note();
        return orphanedBookingService.reconcile(id, note, principal.getId())
                .map(booking -> ResponseEntity.ok(OrphanedBookingResponse.from(booking)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
