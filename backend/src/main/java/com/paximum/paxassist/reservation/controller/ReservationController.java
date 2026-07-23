package com.paximum.paxassist.reservation.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.paximum.paxassist.auth.security.UserPrincipal;
import com.paximum.paxassist.reservation.domain.ReservationCaller;
import com.paximum.paxassist.reservation.service.CancelResult;
import com.paximum.paxassist.reservation.service.ConfirmationResult;
import com.paximum.paxassist.reservation.service.PreviewResult;
import com.paximum.paxassist.reservation.service.ReservationService;
import com.paximum.paxassist.reservation.service.command.PreviewReservationCommand;
import com.paximum.paxassist.reservation.web.ReservationWebMapper;
import com.paximum.paxassist.reservation.web.dto.CancelRequest;
import com.paximum.paxassist.reservation.web.dto.ConfirmRequest;
import com.paximum.paxassist.reservation.web.dto.NeedsConfirmationResponse;
import com.paximum.paxassist.reservation.web.dto.OutcomeResponse;
import com.paximum.paxassist.reservation.web.dto.PreviewResponse;
import com.paximum.paxassist.reservation.web.dto.ReservationSummaryResponse;

import jakarta.validation.Valid;

/**
 * Reservation HTTP API. Thin plumbing over {@link ReservationService}; no business logic here.
 *
 * <p>The caller is resolved server-side, never taken from the request body: a logged-in user from the
 * JWT principal ({@link UserPrincipal#getId()}), or an anonymous guest from the opaque
 * {@code X-Guest-Id} header — the same identity scheme the chat module already uses.
 *
 * <p><b>Who may call what</b> (the path rules live centrally in {@code SecurityConfig}):
 * <ul>
 *   <li>{@code POST /preview}, {@code POST /} and {@code GET /lookup} are open to guests — booking no
 *       longer requires an account. Preview/confirm still need <em>an</em> identity, so the
 *       preview → confirm handoff stays scoped to the browser that started it.</li>
 *   <li>{@code GET /}, {@code GET /{id}} and cancel remain account-only: they are keyed by user id
 *       and a reservation list is exactly what a guest does not have. A guest reaches their booking
 *       through {@code GET /lookup} (PNR + surname) instead.</li>
 * </ul>
 *
 * <p>Ownership is enforced in the service for every id-scoped operation (confirm, detail, cancel).
 */
@RestController
@RequestMapping("/api/v1/reservations")
public class ReservationController {

    /** Bound by the {@code guest_token varchar(64)} column; longer values are rejected, not truncated. */
    private static final int MAX_GUEST_ID_LENGTH = 64;
    private static final String GUEST_ID_HEADER = "X-Guest-Id";

    private final ReservationService reservationService;
    private final ReservationWebMapper mapper;

    public ReservationController(ReservationService reservationService, ReservationWebMapper mapper) {
        this.reservationService = reservationService;
        this.mapper = mapper;
    }

    /**
     * POST /api/v1/reservations/preview — re-price and re-check availability against TourVisio, then
     * freeze the summary. Still no purchase and no DB write.
     */
    @PostMapping("/preview")
    public ResponseEntity<?> preview(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestHeader(value = GUEST_ID_HEADER, required = false) String guestId,
            @Valid @RequestBody PreviewReservationCommand command) {
        PreviewResult result = reservationService.previewReservation(command.withCaller(resolveCaller(principal, guestId)));
        return switch (result) {
            case PreviewResult.Priced p -> ResponseEntity.ok(mapper.toPreviewResponse(p.preview()));
            // The offer is gone (sold out / expired). The flow stops here with a plain explanation
            // instead of failing later, mid-transaction, on an opaque provider error.
            case PreviewResult.Unavailable ignored -> ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(new OutcomeResponse("PRODUCT_UNAVAILABLE",
                            "Seçtiğiniz ürün artık müsait değil. Lütfen aramanızı yenileyip tekrar seçin."));
            // Availability could NOT be verified, so nothing was frozen — better than freezing a price
            // we never checked.
            case PreviewResult.ProviderUnavailable ignored -> ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .body(new OutcomeResponse("PROVIDER_UNAVAILABLE",
                            "Rezervasyon sistemine şu anda ulaşılamıyor. Lütfen birazdan tekrar deneyin."));
        };
    }

    /**
     * POST /api/v1/reservations — final confirm (the only call that triggers TourVisio). A body with
     * {@code confirmationToken} instead of {@code previewId} resumes a booking that paused on a warning.
     */
    @PostMapping
    public ResponseEntity<?> confirm(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestHeader(value = GUEST_ID_HEADER, required = false) String guestId,
            @Valid @RequestBody ConfirmRequest request) {
        ReservationCaller caller = resolveCaller(principal, guestId);
        ConfirmationResult result = request.confirmationToken() != null && !request.confirmationToken().isBlank()
                ? reservationService.confirmReservationAfterWarning(request.confirmationToken(), caller)
                : reservationService.confirmReservation(request.previewId(), caller);
        return toConfirmResponse(result);
    }

    /**
     * GET /api/v1/reservations/lookup?pnr=…&amp;surname=… — public retrieval of a single booking.
     * Open to guests by design: it is the only way back to a reservation made without an account.
     *
     * <p>The surname is the second factor guarding the PNR, so a wrong pair and an unknown PNR must be
     * indistinguishable — both return 404. Reporting "PNR exists, wrong surname" would turn this into
     * a PNR oracle, and the response body carries passenger contact details.
     */
    @GetMapping("/lookup")
    public ResponseEntity<?> lookup(@RequestParam String pnr, @RequestParam String surname) {
        return reservationService.lookupReservation(pnr, surname)
                .<ResponseEntity<?>>map(r -> ResponseEntity.ok(mapper.toDetail(r, List.of())))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(new OutcomeResponse("RESERVATION_NOT_FOUND",
                                "Bu rezervasyon numarası ve soyadla eşleşen bir rezervasyon bulunamadı.")));
    }

    /** GET /api/v1/reservations — current user's reservations. */
    @GetMapping
    public ResponseEntity<List<ReservationSummaryResponse>> list(
            @AuthenticationPrincipal UserPrincipal principal) {
        List<ReservationSummaryResponse> body = reservationService.listReservations(principal.getId()).stream()
                .map(mapper::toSummary)
                .toList();
        return ResponseEntity.ok(body);
    }

    /** GET /api/v1/reservations/{id} — detail incl. live TourVisio cancellation options. Owner-only. */
    @GetMapping("/{id}")
    public ResponseEntity<?> detail(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal principal) {
        return reservationService.getReservationDetail(id, principal.getId())
                .<ResponseEntity<?>>map(d -> ResponseEntity.ok(mapper.toDetail(d.reservation(), d.cancellationOptions())))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /** PATCH /api/v1/reservations/{id}/cancel — cancel via TourVisio and update local status. Owner-only. */
    @PatchMapping("/{id}/cancel")
    public ResponseEntity<?> cancel(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody CancelRequest request) {
        CancelResult result = reservationService.cancelReservation(id, principal.getId(), request.reason(), request.serviceIds());
        return toCancelResponse(id, result);
    }

    /**
     * A logged-in user wins over any guest header; otherwise the request must carry a usable
     * {@code X-Guest-Id}. A request with neither identity is unauthorized — the preview it creates
     * would be owned by nobody, and the confirm that follows could then be issued by anyone.
     * An over-length guest id is a bad request (it would overflow the column).
     */
    private ReservationCaller resolveCaller(UserPrincipal principal, String guestId) {
        if (principal != null) {
            return ReservationCaller.authenticated(principal.getId());
        }
        String trimmed = guestId == null ? "" : guestId.trim();
        if (trimmed.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                    "Authentication or a guest session (X-Guest-Id) is required.");
        }
        if (trimmed.length() > MAX_GUEST_ID_LENGTH) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid guest session id.");
        }
        return ReservationCaller.guest(trimmed);
    }

    // --- Result -> HTTP mapping ---------------------------------------------------------------------
    //
    // Every message below is a fixed, user-facing sentence. The provider's own text and our internal
    // descriptions ("read timed out", host/port, exception detail) stay in the logs: they mean nothing
    // to a traveller and they expose how the system is wired. The outcome CODE is what the frontend
    // branches on; the message is what the user reads.

    private ResponseEntity<?> toConfirmResponse(ConfirmationResult result) {
        return switch (result) {
            case ConfirmationResult.Confirmed c -> reservationService.getReservation(c.reservationId())
                    .<ResponseEntity<?>>map(r -> ResponseEntity.status(HttpStatus.CREATED).body(mapper.toSummary(r)))
                    .orElseGet(() -> ResponseEntity.status(HttpStatus.CREATED)
                            .body(new OutcomeResponse("CONFIRMED",
                                    "Rezervasyonunuz alındı. Rezervasyon numaranız: " + c.reservationNumber())));
            case ConfirmationResult.NeedsUserConfirmation n ->
                    ResponseEntity.ok(new NeedsConfirmationResponse(n.confirmationToken(), n.warnings()));
            case ConfirmationResult.PreviewExpired ignored ->
                    ResponseEntity.status(HttpStatus.GONE).body(new OutcomeResponse("PREVIEW_EXPIRED",
                            "Önizlemenizin süresi doldu. Lütfen rezervasyonu yeniden başlatın."));
            case ConfirmationResult.OwnershipMismatch ignored ->
                    ResponseEntity.status(HttpStatus.FORBIDDEN).body(new OutcomeResponse("OWNERSHIP_MISMATCH",
                            "Bu önizleme size ait değil."));
            case ConfirmationResult.DuplicateInProgress ignored ->
                    ResponseEntity.status(HttpStatus.CONFLICT).body(new OutcomeResponse("DUPLICATE_IN_PROGRESS",
                            "Bu rezervasyon zaten onaylanıyor. Lütfen tekrar göndermeyin."));
            case ConfirmationResult.TourVisioRejected ignored ->
                    ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(new OutcomeResponse("TOURVISIO_REJECTED",
                            "Rezervasyonunuz tamamlanamadı. Rezervasyon yapılmadı; lütfen aramanızı yenileyip tekrar deneyin."));
            case ConfirmationResult.TourVisioUnavailable ignored ->
                    ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(new OutcomeResponse("TOURVISIO_UNAVAILABLE",
                            "Rezervasyon sistemine şu anda ulaşılamıyor. Rezervasyon yapılmadı; lütfen birazdan tekrar deneyin."));
            // 202, not an error: the purchase MAY have gone through. Never tell the user it failed —
            // that invites a retry and a double booking.
            case ConfirmationResult.CommitOutcomeUnknown ignored ->
                    ResponseEntity.status(HttpStatus.ACCEPTED).body(new OutcomeResponse("COMMIT_OUTCOME_UNKNOWN",
                            "Rezervasyonunuz işleniyor ancak sonucu henüz doğrulayamadık. "
                                    + "Lütfen tekrar göndermeyin; birkaç dakika içinde \"Rezervasyonlarım\" sayfasından kontrol edin."));
            case ConfirmationResult.OrphanedBooking ignored ->
                    ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new OutcomeResponse("ORPHANED_BOOKING",
                            "Rezervasyonunuz sağlayıcıda oluşturuldu ancak kaydımıza işlenemedi. "
                                    + "Ekibimiz durumu inceliyor; lütfen tekrar göndermeyin."));
            // 409: the price moved between preview and confirm. Nothing was purchased — both amounts go
            // back so the UI can show old vs new and send the user to preview again.
            case ConfirmationResult.PriceMismatch p ->
                    ResponseEntity.status(HttpStatus.CONFLICT).body(new OutcomeResponse("PRICE_MISMATCH",
                            "Ürünün fiyatı değişti (onayladığınız: " + p.declaredAmount() + " " + p.currency()
                                    + ", güncel: " + p.actualAmount() + " " + p.currency()
                                    + "). Rezervasyon yapılmadı; lütfen yeniden önizleyin."));
        };
    }

    private ResponseEntity<?> toCancelResponse(Long id, CancelResult result) {
        return switch (result) {
            case CancelResult.Cancelled ignored ->
                    ResponseEntity.ok(new OutcomeResponse("CANCELLED", "Rezervasyonunuz iptal edildi."));
            case CancelResult.NotFound ignored -> ResponseEntity.notFound().build();
            case CancelResult.NotCancellable ignored ->
                    ResponseEntity.status(HttpStatus.CONFLICT).body(new OutcomeResponse("NOT_CANCELLABLE",
                            "Bu rezervasyon iptal edilemiyor."));
            case CancelResult.TourVisioRejected ignored ->
                    ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(new OutcomeResponse("TOURVISIO_REJECTED",
                            "İptal talebiniz sağlayıcı tarafından reddedildi. Rezervasyonunuz değişmedi."));
            // 202 (not a failure): the sale is still settling right after booking, so the provider won't
            // accept the cancellation YET. Nothing changed and it is retryable — the frontend renders any
            // 202 as a warning + this message (not a red error, not a false "cancelled").
            case CancelResult.NotReadyYet ignored ->
                    ResponseEntity.status(HttpStatus.ACCEPTED).body(new OutcomeResponse("CANCEL_NOT_READY",
                            "Rezervasyon yeni oluşturulduğu için iptal şu an yapılamıyor. "
                                    + "İşlem tamamlanınca birkaç dakika içinde tekrar deneyin."));
            case CancelResult.TourVisioUnavailable ignored ->
                    ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(new OutcomeResponse("TOURVISIO_UNAVAILABLE",
                            "Rezervasyon sistemine şu anda ulaşılamıyor. Rezervasyonunuz değişmedi; lütfen birazdan tekrar deneyin."));
            // 202: the cancellation MAY have gone through — do not claim it failed.
            case CancelResult.OutcomeUnknown ignored ->
                    ResponseEntity.status(HttpStatus.ACCEPTED).body(new OutcomeResponse("CANCEL_OUTCOME_UNKNOWN",
                            "İptal talebiniz alındı ancak sonucu henüz doğrulayamadık. "
                                    + "Lütfen tekrar göndermeyin; birkaç dakika içinde rezervasyonunuzun durumunu kontrol edin."));
        };
    }
}
