package com.paximum.paxassist.reservation.web;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.paximum.paxassist.reservation.service.CancelResult;
import com.paximum.paxassist.reservation.service.ConfirmationResult;
import com.paximum.paxassist.reservation.service.ReservationPreview;
import com.paximum.paxassist.reservation.service.ReservationService;
import com.paximum.paxassist.reservation.service.command.PreviewReservationCommand;
import com.paximum.paxassist.reservation.web.dto.CancelRequest;
import com.paximum.paxassist.reservation.web.dto.ConfirmRequest;
import com.paximum.paxassist.reservation.web.dto.NeedsConfirmationResponse;
import com.paximum.paxassist.reservation.web.dto.OutcomeResponse;
import com.paximum.paxassist.reservation.web.dto.PreviewResponse;
import com.paximum.paxassist.reservation.web.dto.ReservationSummaryResponse;

/**
 * Reservation HTTP API (ticket 4). Thin plumbing over {@link ReservationService}; no business logic here.
 *
 * <p><b>Deferred to ticket 5 (deliberately not built):</b> Bean Validation on request bodies, a global
 * exception handler / standard error contract, and any real current-user / ownership enforcement. The
 * {@code X-User-Id} header is an unvalidated placeholder so the wiring exists — no security logic is
 * built around it yet.
 */
@RestController
@RequestMapping("/api/v1/reservations")
public class ReservationController {

    private static final String USER_ID_HEADER = "X-User-Id";

    private final ReservationService reservationService;
    private final ReservationWebMapper mapper;

    public ReservationController(ReservationService reservationService, ReservationWebMapper mapper) {
        this.reservationService = reservationService;
        this.mapper = mapper;
    }

    /** POST /api/v1/reservations/preview — freeze a summary (no TourVisio, no DB write). */
    @PostMapping("/preview")
    public ResponseEntity<PreviewResponse> preview(
            @RequestHeader(value = USER_ID_HEADER, required = false) Long userId,
            @RequestBody PreviewReservationCommand command) {
        Long effectiveUserId = userId != null ? userId : command.userId();
        ReservationPreview preview = reservationService.previewReservation(command.withUserId(effectiveUserId));
        return ResponseEntity.ok(mapper.toPreviewResponse(preview));
    }

    /**
     * POST /api/v1/reservations — final confirm (the only call that triggers TourVisio). A body with
     * {@code confirmationToken} instead of {@code previewId} resumes a booking that paused on a warning.
     */
    @PostMapping
    public ResponseEntity<?> confirm(
            @RequestHeader(value = USER_ID_HEADER, required = false) Long userId,
            @RequestBody ConfirmRequest request) {
        ConfirmationResult result = request.confirmationToken() != null && !request.confirmationToken().isBlank()
                ? reservationService.confirmReservationAfterWarning(request.confirmationToken(), userId)
                : reservationService.confirmReservation(request.previewId(), userId);
        return toConfirmResponse(result);
    }

    /** GET /api/v1/reservations — current user's reservations. */
    @GetMapping
    public ResponseEntity<List<ReservationSummaryResponse>> list(
            @RequestHeader(value = USER_ID_HEADER, required = false) Long userId) {
        List<ReservationSummaryResponse> body = reservationService.listReservations(userId).stream()
                .map(mapper::toSummary)
                .toList();
        return ResponseEntity.ok(body);
    }

    /** GET /api/v1/reservations/{id} — detail incl. live TourVisio cancellation options. */
    @GetMapping("/{id}")
    public ResponseEntity<?> detail(
            @PathVariable Long id,
            @RequestHeader(value = USER_ID_HEADER, required = false) Long userId) {
        return reservationService.getReservationDetail(id)
                .<ResponseEntity<?>>map(d -> ResponseEntity.ok(mapper.toDetail(d.reservation(), d.cancellationOptions())))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /** PATCH /api/v1/reservations/{id}/cancel — cancel via TourVisio and update local status. */
    @PatchMapping("/{id}/cancel")
    public ResponseEntity<?> cancel(
            @PathVariable Long id,
            @RequestHeader(value = USER_ID_HEADER, required = false) Long userId,
            @RequestBody CancelRequest request) {
        CancelResult result = reservationService.cancelReservation(id, request.reason(), request.serviceIds());
        return toCancelResponse(id, result);
    }

    // --- Result -> HTTP mapping (plain plumbing; standardized error contract is ticket 5) ------------

    private ResponseEntity<?> toConfirmResponse(ConfirmationResult result) {
        return switch (result) {
            case ConfirmationResult.Confirmed c -> reservationService.getReservation(c.reservationId())
                    .<ResponseEntity<?>>map(r -> ResponseEntity.status(HttpStatus.CREATED).body(mapper.toSummary(r)))
                    .orElseGet(() -> ResponseEntity.status(HttpStatus.CREATED)
                            .body(new OutcomeResponse("CONFIRMED", "Reservation " + c.reservationNumber() + " created")));
            case ConfirmationResult.NeedsUserConfirmation n ->
                    ResponseEntity.ok(new NeedsConfirmationResponse(n.confirmationToken(), n.warnings()));
            case ConfirmationResult.PreviewExpired ignored ->
                    ResponseEntity.status(HttpStatus.GONE).body(new OutcomeResponse("PREVIEW_EXPIRED", "Preview expired, please start again."));
            case ConfirmationResult.OwnershipMismatch ignored ->
                    ResponseEntity.status(HttpStatus.FORBIDDEN).body(new OutcomeResponse("OWNERSHIP_MISMATCH", "This preview belongs to another user."));
            case ConfirmationResult.DuplicateInProgress ignored ->
                    ResponseEntity.status(HttpStatus.CONFLICT).body(new OutcomeResponse("DUPLICATE_IN_PROGRESS", "This reservation is already being confirmed."));
            case ConfirmationResult.TourVisioRejected r ->
                    ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(new OutcomeResponse("TOURVISIO_REJECTED", r.message()));
            case ConfirmationResult.TourVisioUnavailable u ->
                    ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(new OutcomeResponse("TOURVISIO_UNAVAILABLE", u.description()));
            case ConfirmationResult.CommitOutcomeUnknown u ->
                    ResponseEntity.status(HttpStatus.ACCEPTED).body(new OutcomeResponse("COMMIT_OUTCOME_UNKNOWN", u.description()));
            case ConfirmationResult.OrphanedBooking o ->
                    ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new OutcomeResponse("ORPHANED_BOOKING", o.description()));
        };
    }

    private ResponseEntity<?> toCancelResponse(Long id, CancelResult result) {
        return switch (result) {
            case CancelResult.Cancelled c ->
                    ResponseEntity.ok(new OutcomeResponse("CANCELLED", "Reservation " + id + " is now " + c.newStatus()));
            case CancelResult.NotFound ignored -> ResponseEntity.notFound().build();
            case CancelResult.NotCancellable n ->
                    ResponseEntity.status(HttpStatus.CONFLICT).body(new OutcomeResponse("NOT_CANCELLABLE", n.reason()));
            case CancelResult.TourVisioRejected r ->
                    ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(new OutcomeResponse("TOURVISIO_REJECTED", r.message()));
            case CancelResult.TourVisioUnavailable u ->
                    ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(new OutcomeResponse("TOURVISIO_UNAVAILABLE", u.description()));
            case CancelResult.OutcomeUnknown u ->
                    ResponseEntity.status(HttpStatus.ACCEPTED).body(new OutcomeResponse("CANCEL_OUTCOME_UNKNOWN", u.description()));
        };
    }
}
