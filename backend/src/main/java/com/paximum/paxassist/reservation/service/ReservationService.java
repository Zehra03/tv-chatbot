package com.paximum.paxassist.reservation.service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.paximum.paxassist.reservation.domain.ProductType;
import com.paximum.paxassist.reservation.domain.Reservation;
import com.paximum.paxassist.reservation.domain.ReservationStatus;
import com.paximum.paxassist.reservation.infrastructure.tourvisio.client.TourVisioBookingClient;
import com.paximum.paxassist.reservation.infrastructure.tourvisio.dto.request.AddServicesRequest;
import com.paximum.paxassist.reservation.infrastructure.tourvisio.dto.response.CancelPenalty;
import com.paximum.paxassist.reservation.infrastructure.tourvisio.dto.response.CancelReservationResponse;
import com.paximum.paxassist.reservation.infrastructure.tourvisio.dto.response.CancellationPenaltyResponse;
import com.paximum.paxassist.reservation.infrastructure.tourvisio.dto.response.CommitTransactionResponse;
import com.paximum.paxassist.reservation.infrastructure.tourvisio.dto.response.TourVisioResponseHeader;
import com.paximum.paxassist.reservation.infrastructure.tourvisio.dto.response.TransactionResponse;
import com.paximum.paxassist.reservation.infrastructure.tourvisio.result.TourVisioCallResult;
import com.paximum.paxassist.reservation.infrastructure.tourvisio.result.TourVisioCallResult.BusinessFailure;
import com.paximum.paxassist.reservation.infrastructure.tourvisio.result.TourVisioCallResult.Success;
import com.paximum.paxassist.reservation.infrastructure.tourvisio.result.TourVisioCallResult.TechnicalFailure;
import com.paximum.paxassist.reservation.infrastructure.tourvisio.result.TourVisioCallResult.UnknownOutcome;
import com.paximum.paxassist.reservation.pending.AwaitingCommit;
import com.paximum.paxassist.reservation.pending.PendingReservation;
import com.paximum.paxassist.reservation.pending.PendingReservationStore;
import com.paximum.paxassist.reservation.recovery.OrphanedBooking;
import com.paximum.paxassist.reservation.recovery.OrphanedBookingRepository;
import com.paximum.paxassist.reservation.repository.ReservationRepository;
import com.paximum.paxassist.reservation.service.command.PreviewReservationCommand;

/**
 * Core reservation business logic: a two-step <b>preview → confirm</b> flow with a strict guarantee
 * that <b>the TourVisio client is never triggered — in any way — until an explicit final-confirm
 * signal</b>.
 *
 * <ul>
 *   <li>{@link #previewReservation} freezes a validated snapshot in Redis and returns a summary. It
 *       makes <b>zero</b> TourVisio calls and <b>zero</b> DB writes — there is no injected code path
 *       from here into either.</li>
 *   <li>{@link #confirmReservation} is the only entry that talks to TourVisio: it claims the snapshot
 *       atomically (duplicate guard), checks ownership, then runs begin → add → setInfo → commit.</li>
 *   <li>{@link #confirmReservationAfterWarning} resumes at commit after the user acknowledges a
 *       {@code setReservationInfo} warning (e.g. DuplicateReservationFound).</li>
 * </ul>
 *
 * <p><b>Accepted risk (flagged):</b> the frozen price/availability is trusted as-is — there is no
 * re-verification against the Hotel/Flight modules before confirming, because no such live-recheck
 * method exists there yet. Revisit when one does.
 */
@Service
public class ReservationService {

    private static final Logger log = LoggerFactory.getLogger(ReservationService.class);
    private static final DateTimeFormatter RES_NO_DATE = DateTimeFormatter.BASIC_ISO_DATE; // yyyyMMdd

    private final PendingReservationStore pendingStore;
    private final TourVisioBookingClient bookingClient;
    private final ReservationTourVisioRequestMapper requestMapper;
    private final ReservationEntityMapper entityMapper;
    private final ReservationRepository reservationRepository;
    private final OrphanedBookingRepository orphanedBookingRepository;

    public ReservationService(
            PendingReservationStore pendingStore,
            TourVisioBookingClient bookingClient,
            ReservationTourVisioRequestMapper requestMapper,
            ReservationEntityMapper entityMapper,
            ReservationRepository reservationRepository,
            OrphanedBookingRepository orphanedBookingRepository) {
        this.pendingStore = pendingStore;
        this.bookingClient = bookingClient;
        this.requestMapper = requestMapper;
        this.entityMapper = entityMapper;
        this.reservationRepository = reservationRepository;
        this.orphanedBookingRepository = orphanedBookingRepository;
    }

    // =========================================================================================
    // Step 1 — PREVIEW. No TourVisio. No DB.
    // =========================================================================================

    /**
     * Freezes the validated input as a Redis snapshot and returns a summary. Makes no TourVisio call
     * and no DB write of any kind.
     */
    public ReservationPreview previewReservation(PreviewReservationCommand command) {
        ProductType productType = deriveProductType(command); // also validates at least one detail present

        String previewId = UUID.randomUUID().toString();
        PendingReservation snapshot = new PendingReservation(previewId, command.userId(), Instant.now(), command);
        pendingStore.savePreview(snapshot);

        log.info("Reservation preview created: previewId={}, productType={}, ttl={}", previewId, productType, pendingStore.previewTtl());
        return new ReservationPreview(
                previewId,
                Instant.now().plus(pendingStore.previewTtl()),
                productType,
                command.totalAmount(),
                command.currency(),
                command.leadGuestName(),
                passengerNames(command),
                command.hotel() != null,
                command.flight() != null);
    }

    // =========================================================================================
    // Step 2 — CONFIRM. The only place TourVisio is ever called.
    // =========================================================================================

    /**
     * Final confirm: atomically claims the snapshot (concurrency guard), checks ownership, then runs
     * the TourVisio transaction flow (begin → add → setInfo → commit) and persists on success.
     */
    public ConfirmationResult confirmReservation(String previewId, Long userId) {
        // Non-destructive pre-check so an ownership mismatch or plain expiry doesn't consume a valid preview.
        Optional<PendingReservation> peek = pendingStore.peekPreview(previewId);
        if (peek.isEmpty()) {
            return new ConfirmationResult.PreviewExpired();
        }
        // Ownership check belongs here even though full enforcement is finalized in ticket 5.
        if (!Objects.equals(peek.get().userId(), userId)) {
            log.warn("Ownership mismatch on confirm: previewId={} requested by a non-owner", previewId);
            return new ConfirmationResult.OwnershipMismatch();
        }
        // Atomic claim (GETDEL): only ONE concurrent/duplicate confirm wins; the rest get empty here.
        Optional<PendingReservation> claimed = pendingStore.claimPreview(previewId);
        if (claimed.isEmpty()) {
            log.warn("Duplicate/concurrent confirm lost the claim race: previewId={}", previewId);
            return new ConfirmationResult.DuplicateInProgress();
        }

        return runTransactionFlow(claimed.get().command(), userId);
    }

    /**
     * Resumes at commit after the user acknowledges a {@code setReservationInfo} warning. Atomically
     * claims the awaiting-commit state so a duplicate acknowledgement cannot commit twice.
     */
    public ConfirmationResult confirmReservationAfterWarning(String confirmationToken, Long userId) {
        Optional<AwaitingCommit> peek = pendingStore.peekAwaitingCommit(confirmationToken);
        if (peek.isEmpty()) {
            return new ConfirmationResult.PreviewExpired();
        }
        if (!Objects.equals(peek.get().userId(), userId)) {
            log.warn("Ownership mismatch on second confirm: token rejected");
            return new ConfirmationResult.OwnershipMismatch();
        }
        Optional<AwaitingCommit> claimed = pendingStore.claimAwaitingCommit(confirmationToken);
        if (claimed.isEmpty()) {
            log.warn("Duplicate/concurrent second-confirm lost the claim race");
            return new ConfirmationResult.DuplicateInProgress();
        }
        AwaitingCommit awaiting = claimed.get();
        return commitAndPersist(awaiting.command(), awaiting.transactionId(), userId);
    }

    // =========================================================================================
    // Read & cancel operations (ticket 4 — added here to keep the controller thin)
    // =========================================================================================

    /** Current user's reservations, newest first. */
    @Transactional(readOnly = true)
    public List<Reservation> listReservations(Long userId) {
        return reservationRepository.findByUserIdOrderByReservationDateDescIdDesc(userId);
    }

    /** Plain load by id (no TourVisio call) — used e.g. to render a just-created reservation. */
    @Transactional(readOnly = true)
    public Optional<Reservation> getReservation(Long id) {
        return reservationRepository.findById(id).map(this::initializeAssociations);
    }

    /**
     * Reservation detail plus its live TourVisio cancellation options.
     *
     * <p>NOTE (flagged): the TourVisio {@code getCancellationPenalty} call currently runs on every
     * detail view (and within this read transaction). Possible optimizations for later: only fetch
     * when the status still allows cancellation, and/or move the HTTP call outside the DB transaction.
     * Not changing the endpoint contract here.
     */
    @Transactional(readOnly = true)
    public Optional<ReservationDetailResult> getReservationDetail(Long id) {
        return reservationRepository.findById(id)
                .map(this::initializeAssociations)
                .map(reservation -> new ReservationDetailResult(reservation, fetchCancellationOptions(reservation)));
    }

    /**
     * Cancels via TourVisio using the reservation's external reference, then maps the returned status
     * onto our {@link ReservationStatus} and persists it. No local status change on any failure.
     */
    @Transactional
    public CancelResult cancelReservation(Long id, String reason, List<String> serviceIds) {
        Optional<Reservation> found = reservationRepository.findById(id);
        if (found.isEmpty()) {
            return new CancelResult.NotFound();
        }
        Reservation reservation = found.get();
        String external = reservation.getExternalReservationNumber();
        if (external == null || external.isBlank()) {
            return new CancelResult.NotCancellable("reservation has no TourVisio external reference");
        }

        TourVisioCallResult<CancelReservationResponse> result = bookingClient.cancelReservation(external, reason, serviceIds);

        if (result instanceof UnknownOutcome<CancelReservationResponse> unknown) {
            log.error("CANCEL AMBIGUOUS: reservationId={}, external={} — verify via getReservationDetail before assuming state. {}",
                    id, external, unknown.description());
            return new CancelResult.OutcomeUnknown(unknown.description());
        }
        if (result instanceof BusinessFailure<CancelReservationResponse> rejected) {
            TourVisioResponseHeader header = rejected.header();
            return new CancelResult.TourVisioRejected(header == null ? null : header.primaryCode(), firstMessage(header));
        }
        if (!(result instanceof Success<CancelReservationResponse> success)) {
            String description = result instanceof TechnicalFailure<CancelReservationResponse> tf ? tf.description() : "unknown technical failure";
            return new CancelResult.TourVisioUnavailable(description);
        }

        Integer tourVisioStatus = success.body() != null && success.body().body() != null
                ? success.body().body().reservationStatus() : null;
        ReservationStatus newStatus = mapReservationStatus(tourVisioStatus);
        reservation.setStatus(newStatus);
        reservationRepository.save(reservation);
        log.info("Reservation cancelled: id={}, external={}, tourVisioStatus={}, newStatus={}", id, external, tourVisioStatus, newStatus);
        return new CancelResult.Cancelled(newStatus);
    }

    private List<CancelPenalty> fetchCancellationOptions(Reservation reservation) {
        String external = reservation.getExternalReservationNumber();
        if (external == null || external.isBlank()) {
            return List.of();
        }
        TourVisioCallResult<CancellationPenaltyResponse> result = bookingClient.getCancellationPenalty(external);
        if (result instanceof Success<CancellationPenaltyResponse> success
                && success.body() != null && success.body().body() != null
                && success.body().body().cancelPenalties() != null) {
            return success.body().body().cancelPenalties();
        }
        log.warn("Cancellation options unavailable for reservationId={} (external={}): {}",
                reservation.getId(), external, result.getClass().getSimpleName());
        return List.of();
    }

    /** Forces the lazy associations to load inside the transaction so the detached entity can be mapped. */
    private Reservation initializeAssociations(Reservation reservation) {
        reservation.getPassengers().size();
        if (reservation.getHotelDetails() != null) {
            reservation.getHotelDetails().getHotelName();
        }
        if (reservation.getFlightDetails() != null) {
            reservation.getFlightDetails().getOrigin();
        }
        return reservation;
    }

    /**
     * Maps TourVisio's integer {@code reservationStatus} to our {@link ReservationStatus}.
     *
     * <p>TODO (ASK — flagged to product owner): the concrete TourVisio status codes are not documented
     * here, so this defaults to {@code CANCELLED} on a successful cancel call (a successful
     * cancelReservation logically means the booking is cancelled). Replace with the real code mapping
     * once confirmed (e.g. to distinguish full vs partial cancellation).
     */
    private ReservationStatus mapReservationStatus(Integer tourVisioStatus) {
        return ReservationStatus.CANCELLED;
    }

    // =========================================================================================
    // TourVisio transaction flow
    // =========================================================================================

    private ConfirmationResult runTransactionFlow(PreviewReservationCommand command, Long userId) {
        // begin
        TourVisioCallResult<TransactionResponse> begin =
                bookingClient.beginTransactionWithOffer(requestMapper.toBeginRequest(command));
        if (!(begin instanceof Success<TransactionResponse>)) {
            return stepFailure("beginTransaction", begin);
        }
        String transactionId = transactionIdOf(begin);
        if (transactionId == null) {
            return new ConfirmationResult.TourVisioUnavailable("beginTransaction", "response carried no transactionId");
        }

        // add services (optional extras)
        Optional<AddServicesRequest> addRequest = requestMapper.toAddServicesRequest(command, transactionId);
        if (addRequest.isPresent()) {
            TourVisioCallResult<TransactionResponse> add = bookingClient.addServices(addRequest.get());
            if (!(add instanceof Success<TransactionResponse>)) {
                return stepFailure("addServices", add);
            }
        }

        // set reservation info
        TourVisioCallResult<TransactionResponse> set =
                bookingClient.setReservationInfo(requestMapper.toSetReservationInfoRequest(command, transactionId));
        if (!(set instanceof Success<TransactionResponse> setSuccess)) {
            return stepFailure("setReservationInfo", set);
        }

        // Warning (messageType 4, e.g. DuplicateReservationFound): do NOT commit — require a 2nd confirm.
        TourVisioResponseHeader setHeader = setSuccess.body() == null ? null : setSuccess.body().header();
        if (setHeader != null && setHeader.requiresConfirmation()) {
            String token = UUID.randomUUID().toString();
            List<String> warnings = warningMessages(setHeader);
            pendingStore.saveAwaitingCommit(new AwaitingCommit(token, userId, transactionId, Instant.now(), command, warnings));
            log.info("setReservationInfo needs user confirmation ({}). Awaiting explicit second confirm.", warnings);
            return new ConfirmationResult.NeedsUserConfirmation(token, warnings);
        }

        return commitAndPersist(command, transactionId, userId);
    }

    private ConfirmationResult commitAndPersist(PreviewReservationCommand command, String transactionId, Long userId) {
        TourVisioCallResult<CommitTransactionResponse> commit =
                bookingClient.commitTransaction(requestMapper.toCommitRequest(command, transactionId));

        // Ambiguous: the purchase MAY have happened. Do not persist a normal reservation; must reconcile.
        if (commit instanceof UnknownOutcome<CommitTransactionResponse> unknown) {
            log.error("COMMIT AMBIGUOUS (transactionId={}): purchase may have occurred; verify via getReservationDetail before assuming failure. {}",
                    transactionId, unknown.description());
            return new ConfirmationResult.CommitOutcomeUnknown(unknown.reservationRef(), unknown.description());
        }
        // Clean business rejection: no purchase happened. Do not persist a reservation (documented decision).
        if (commit instanceof BusinessFailure<CommitTransactionResponse> rejected) {
            TourVisioResponseHeader header = rejected.header();
            log.warn("Commit rejected by TourVisio: code={}", header == null ? null : header.primaryCode());
            return new ConfirmationResult.TourVisioRejected("commitTransaction", header == null ? null : header.primaryCode(), firstMessage(header));
        }
        // Definite technical failure: no purchase happened. Do not persist.
        if (!(commit instanceof Success<CommitTransactionResponse> success)) {
            String description = commit instanceof TechnicalFailure<CommitTransactionResponse> tf ? tf.description() : "unknown technical failure";
            log.warn("Commit failed technically: {}", description);
            return new ConfirmationResult.TourVisioUnavailable("commitTransaction", description);
        }

        // Clean success — a real purchase now exists on TourVisio's side.
        CommitTransactionResponse.Body body = success.body() == null ? null : success.body().body();
        String externalReservationNumber = body == null ? null : body.reservationNumber();
        String reservationNumber = generateReservationNumber();

        Reservation reservation = entityMapper.toReservation(command, reservationNumber, externalReservationNumber);
        try {
            Reservation saved = reservationRepository.save(reservation);
            log.info("Reservation confirmed & persisted: id={}, reservationNumber={}, externalReservationNumber={}",
                    saved.getId(), reservationNumber, externalReservationNumber);
            return new ConfirmationResult.Confirmed(saved.getId(), reservationNumber, externalReservationNumber);
        } catch (Exception dbFailure) {
            // CRITICAL: the purchase already happened on TourVisio but we could not record it locally.
            // This cannot be rolled back. Log at highest severity + persist a durable fallback record.
            handleOrphanedBooking(externalReservationNumber, reservationNumber, transactionId, command, dbFailure);
            return new ConfirmationResult.OrphanedBooking(externalReservationNumber,
                    "Purchase succeeded on TourVisio but local persistence failed; flagged for manual reconciliation.");
        }
    }

    /**
     * Orphaned-booking handling: a TourVisio purchase exists with no local record. Two layers:
     * <ol>
     *   <li>a guaranteed highest-severity ERROR log capturing the external reservation number (so the
     *       booking is recoverable even if the DB is entirely down);</li>
     *   <li>a durable fallback row in the standalone {@code orphaned_bookings} table for reconciliation.
     *       This save is best-effort and defensively guarded — the same DB failure that caused the
     *       orphan may also block it — so its own failure never masks the ERROR log above.</li>
     * </ol>
     */
    private void handleOrphanedBooking(String externalReservationNumber, String reservationNumber,
            String transactionId, PreviewReservationCommand command, Exception dbFailure) {
        log.error("ORPHANED TOURVISIO BOOKING — commit SUCCEEDED but ResDB persist FAILED. "
                        + "externalReservationNumber={}, intendedReservationNumber={}, userId={}, amount={} {}. "
                        + "A real purchase exists with no local record; MANUAL RECONCILIATION REQUIRED.",
                externalReservationNumber, reservationNumber, command.userId(),
                command.totalAmount(), command.currency(), dbFailure);

        try {
            OrphanedBooking record = new OrphanedBooking();
            record.setExternalReservationNumber(externalReservationNumber);
            record.setIntendedReservationNumber(reservationNumber);
            record.setTransactionId(transactionId);
            record.setUserId(command.userId());
            record.setLeadGuestName(command.leadGuestName());
            record.setTotalAmount(command.totalAmount());
            record.setCurrency(command.currency());
            record.setFailureReason(dbFailure.getClass().getName() + ": " + dbFailure.getMessage());
            OrphanedBooking saved = orphanedBookingRepository.save(record);
            log.error("Orphaned booking captured for reconciliation: orphanedBookingId={}, externalReservationNumber={}",
                    saved.getId(), externalReservationNumber);
        } catch (Exception fallbackFailure) {
            log.error("Could NOT persist the orphaned-booking fallback record either (externalReservationNumber={}). "
                    + "The ERROR log above is the only durable trace — reconcile from logs.", externalReservationNumber, fallbackFailure);
        }
    }

    // =========================================================================================
    // Helpers
    // =========================================================================================

    private ProductType deriveProductType(PreviewReservationCommand command) {
        boolean hasHotel = command.hotel() != null;
        boolean hasFlight = command.flight() != null;
        if (hasHotel && hasFlight) {
            return ProductType.COMBINED;
        }
        if (hasHotel) {
            return ProductType.HOTEL;
        }
        if (hasFlight) {
            return ProductType.FLIGHT;
        }
        throw new IllegalArgumentException("Reservation must include at least a hotel or a flight");
    }

    private List<String> passengerNames(PreviewReservationCommand command) {
        if (command.travellers() == null) {
            return List.of();
        }
        return command.travellers().stream()
                .map(t -> (t.firstName() + " " + t.lastName()).trim())
                .toList();
    }

    private String transactionIdOf(TourVisioCallResult<TransactionResponse> result) {
        if (result instanceof Success<TransactionResponse> success
                && success.body() != null && success.body().body() != null) {
            return success.body().body().transactionId();
        }
        return null;
    }

    /** Maps a non-success result of a pre-commit (retryable) step to the matching ConfirmationResult. */
    private ConfirmationResult stepFailure(String step, TourVisioCallResult<?> result) {
        if (result instanceof BusinessFailure<?> businessFailure) {
            TourVisioResponseHeader header = businessFailure.header();
            return new ConfirmationResult.TourVisioRejected(step, header == null ? null : header.primaryCode(), firstMessage(header));
        }
        if (result instanceof UnknownOutcome<?> unknown) {
            // Not expected for retryable steps, but handle defensively.
            return new ConfirmationResult.CommitOutcomeUnknown(unknown.reservationRef(), unknown.description());
        }
        String description = result instanceof TechnicalFailure<?> tf ? tf.description() : "unknown technical failure";
        return new ConfirmationResult.TourVisioUnavailable(step, description);
    }

    private List<String> warningMessages(TourVisioResponseHeader header) {
        if (header == null || header.messages() == null) {
            return List.of();
        }
        return header.messages().stream()
                .filter(m -> m.messageType() != null && m.messageType() == TourVisioResponseHeader.MESSAGE_TYPE_CONFIRMATION_REQUIRED)
                .map(m -> m.message() != null ? m.message() : m.code())
                .toList();
    }

    private String firstMessage(TourVisioResponseHeader header) {
        if (header == null || header.messages() == null || header.messages().isEmpty()) {
            return null;
        }
        return header.messages().get(0).message();
    }

    /** Internal/UI reservation code, e.g. {@code PAX-20260707-A1B2C3}. Uniqueness enforced by the DB. */
    private String generateReservationNumber() {
        String date = LocalDate.now().format(RES_NO_DATE);
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 6).toUpperCase();
        return "PAX-" + date + "-" + suffix;
    }
}
