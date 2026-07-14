package com.paximum.paxassist.reservation.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.JsonNode;

import com.paximum.paxassist.common.log.LogModuleClient;
import com.paximum.paxassist.reservation.domain.ProductType;
import com.paximum.paxassist.reservation.domain.Reservation;
import com.paximum.paxassist.reservation.domain.ReservationStatus;
import com.paximum.paxassist.reservation.infrastructure.tourvisio.client.TourVisioBookingClient;
import com.paximum.paxassist.reservation.infrastructure.tourvisio.dto.request.AddServicesRequest;
import com.paximum.paxassist.reservation.infrastructure.tourvisio.dto.response.CancelPenalty;
import com.paximum.paxassist.reservation.infrastructure.tourvisio.dto.response.CancelReservationResponse;
import com.paximum.paxassist.reservation.infrastructure.tourvisio.dto.response.CancellationPenaltyResponse;
import com.paximum.paxassist.reservation.infrastructure.tourvisio.dto.response.CommitTransactionResponse;
import com.paximum.paxassist.reservation.infrastructure.tourvisio.dto.response.RawTourVisioResponse;
import com.paximum.paxassist.reservation.infrastructure.tourvisio.dto.response.TourVisioPrice;
import com.paximum.paxassist.reservation.infrastructure.tourvisio.dto.response.TourVisioResponseHeader;
import com.paximum.paxassist.reservation.infrastructure.tourvisio.dto.response.TransactionResponse;
import com.paximum.paxassist.reservation.infrastructure.tourvisio.support.TourVisioPriceExtractor;
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
    private final LogModuleClient logModuleClient;

    public ReservationService(
            PendingReservationStore pendingStore,
            TourVisioBookingClient bookingClient,
            ReservationTourVisioRequestMapper requestMapper,
            ReservationEntityMapper entityMapper,
            ReservationRepository reservationRepository,
            OrphanedBookingRepository orphanedBookingRepository,
            LogModuleClient logModuleClient) {
        this.pendingStore = pendingStore;
        this.bookingClient = bookingClient;
        this.requestMapper = requestMapper;
        this.entityMapper = entityMapper;
        this.reservationRepository = reservationRepository;
        this.orphanedBookingRepository = orphanedBookingRepository;
        this.logModuleClient = logModuleClient;
    }

    // =========================================================================================
    // Step 1 — PREVIEW. Re-prices against TourVisio. Still NO purchase and NO DB write.
    // =========================================================================================

    /**
     * Re-validates the offer against TourVisio, then freezes the result as a Redis snapshot and returns
     * the summary (K21: "rezervasyona geçerken güncel fiyat ve müsaitlik yeniden doğrulanır").
     *
     * <p>The price the client sends comes from a search result that may be minutes old — the hotel/flight
     * caches hold results for 5 minutes. Previously the preview trusted it and made no provider call, so a
     * sold-out room or a moved price only surfaced after the user pressed confirm, as an opaque failure
     * mid-transaction. {@code beginTransaction} is TourVisio's own pricing of the offer, and it is not a
     * purchase (only {@code commitTransaction} buys), so it is used here to answer both questions at once:
     * <ul>
     *   <li>a business rejection means the offer is <b>gone</b> → {@link PreviewResult.Unavailable};</li>
     *   <li>a technical failure means availability <b>could not be verified</b> → nothing is frozen, rather
     *       than freezing a price we did not check ({@link PreviewResult.ProviderUnavailable});</li>
     *   <li>on success the <b>provider's price</b> — not the client's — is what gets frozen, so a tampered
     *       or stale {@code totalAmount} can never become the amount recorded in the DB.</li>
     * </ul>
     *
     * <p>The invariant this must not break: <b>no purchase without an explicit confirm</b>. It holds —
     * {@code commitTransaction} is still reached only from {@code confirmReservation}. The transaction
     * opened here is abandoned and expires on TourVisio's side; confirm opens its own and re-verifies the
     * price again, so the check is not weakened by the gap between the two steps.
     */
    public PreviewResult previewReservation(PreviewReservationCommand command) {
        ProductType productType = deriveProductType(command); // also validates at least one detail present

        TourVisioCallResult<TransactionResponse> priced =
                bookingClient.beginTransactionWithOffer(requestMapper.toBeginRequest(command));

        if (priced instanceof BusinessFailure<TransactionResponse> rejected) {
            TourVisioResponseHeader header = rejected.header();
            log.info("Preview refused by TourVisio (offer no longer bookable): code={}",
                    header == null ? null : header.primaryCode());
            return new PreviewResult.Unavailable(firstMessage(header));
        }
        if (!(priced instanceof Success<TransactionResponse> success)) {
            String description = priced instanceof TechnicalFailure<TransactionResponse> tf
                    ? tf.description() : "unknown technical failure";
            log.warn("Preview could not verify availability — TourVisio unavailable: {}", description);
            return new PreviewResult.ProviderUnavailable(description);
        }

        // The live price wins over the client's. A difference is not an error here — it is exactly what
        // the user must be shown before they agree to it.
        BigDecimal declared = command.totalAmount();
        Optional<TourVisioPrice> livePrice = TourVisioPriceExtractor.extract(
                success.body() == null || success.body().body() == null
                        ? null : success.body().body().reservationData());

        BigDecimal amount = livePrice.map(TourVisioPrice::amount).filter(Objects::nonNull).orElse(declared);
        String currency = livePrice.map(TourVisioPrice::currency).filter(Objects::nonNull).orElse(command.currency());
        boolean priceChanged = declared != null && amount.compareTo(declared) != 0;

        if (livePrice.isEmpty()) {
            log.warn("Preview could not read TourVisio's price; freezing the declared amount {} {}.",
                    declared, command.currency());
        } else if (priceChanged) {
            log.info("Price changed since search: {} {} -> {} {}. Freezing the live price.",
                    declared, command.currency(), amount, currency);
        }

        // Freeze what TourVisio actually charges, so confirm and the persisted reservation both carry it.
        PreviewReservationCommand repriced = command.withTotalAmount(amount, currency);
        String previewId = UUID.randomUUID().toString();
        PendingReservation snapshot = new PendingReservation(previewId, command.userId(), Instant.now(), repriced);
        pendingStore.savePreview(snapshot);

        log.info("Reservation preview created: previewId={}, productType={}, ttl={}",
                previewId, productType, pendingStore.previewTtl());
        return new PreviewResult.Priced(new ReservationPreview(
                previewId,
                Instant.now().plus(pendingStore.previewTtl()),
                productType,
                amount,
                currency,
                command.leadGuestName(),
                passengerNames(command),
                command.hotel() != null,
                command.flight() != null,
                priceChanged,
                priceChanged ? declared : null,
                // Availability is not assumed: reaching this line means TourVisio just priced the offer.
                true,
                hotelSummary(command.hotel()),
                flightSummary(command.flight())));
    }

    /** The stay as the user must see it before agreeing to it — dates, nights, rooms, party size. */
    private ReservationPreview.Hotel hotelSummary(PreviewReservationCommand.Hotel hotel) {
        if (hotel == null) {
            return null;
        }
        int nights = (hotel.checkIn() == null || hotel.checkOut() == null)
                ? 0 : (int) ChronoUnit.DAYS.between(hotel.checkIn(), hotel.checkOut());
        return new ReservationPreview.Hotel(
                hotel.hotelName(), hotel.region(), hotel.stars(), hotel.boardType(),
                hotel.checkIn(), hotel.checkOut(), nights,
                hotel.rooms(), hotel.adults(), hotel.children());
    }

    /** The itinerary as the user must see it — route, trip type, departure/return, party size. */
    private ReservationPreview.Flight flightSummary(PreviewReservationCommand.Flight flight) {
        if (flight == null) {
            return null;
        }
        return new ReservationPreview.Flight(
                flight.origin(), flight.destination(), flight.airline(), flight.tripType(),
                flight.departTime(), flight.returnDepartTime(), flight.passengerCount());
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

        PendingReservation snapshot = claimed.get();
        ConfirmationResult result = runTransactionFlow(snapshot.command(), userId);
        // A failure that definitely bought nothing must not cost the user their preview: put the
        // snapshot back so the same previewId can be retried (see restoreIfNothingWasPurchased).
        result = restoreIfNothingWasPurchased(snapshot, result);
        logConfirmOutcome(snapshot.command(), userId, result);
        return result;
    }

    /**
     * The atomic claim (GETDEL) is what stops a double click from buying twice — but it consumes the
     * preview even when the attempt then fails without buying anything. A user retrying a transient
     * TourVisio outage would find their preview gone and be told "this reservation is already being
     * confirmed" (409) while nothing at all had been confirmed.
     *
     * <p>So for the outcomes the flow itself documents as "no purchase happened", the snapshot goes
     * back with its remaining TTL and the same {@code previewId} works again. If it has run out of time,
     * the honest answer is {@code PreviewExpired} (410 → "start again"), never the misleading 409.
     *
     * <p>Deliberately NOT restored: {@link ConfirmationResult.CommitOutcomeUnknown} (the purchase may
     * exist — a retry could book twice), {@link ConfirmationResult.OrphanedBooking} and
     * {@link ConfirmationResult.Confirmed} (bought), {@link ConfirmationResult.NeedsUserConfirmation}
     * (the flow moved on to an awaiting-commit token) and {@link ConfirmationResult.PriceMismatch}
     * (the frozen price is stale by definition — the user must preview again).
     */
    private ConfirmationResult restoreIfNothingWasPurchased(PendingReservation snapshot, ConfirmationResult result) {
        boolean nothingPurchased = result instanceof ConfirmationResult.TourVisioUnavailable
                || result instanceof ConfirmationResult.TourVisioRejected;
        if (!nothingPurchased) {
            return result;
        }
        try {
            if (pendingStore.restorePreview(snapshot)) {
                log.info("Confirm failed without purchasing; preview {} restored for retry.", snapshot.previewId());
                return result;
            }
            log.info("Confirm failed without purchasing, but preview {} had no TTL left.", snapshot.previewId());
        } catch (RuntimeException e) {
            // Redis is down: we cannot hand the preview back, so say so plainly rather than let the
            // next attempt hit the duplicate guard and claim a confirm is in progress.
            log.warn("Could not restore preview {} after a failed confirm: {}", snapshot.previewId(), e.getMessage());
        }
        return new ConfirmationResult.PreviewExpired();
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
        ConfirmationResult result = commitAndPersist(awaiting.command(), awaiting.transactionId(), userId);
        logConfirmOutcome(awaiting.command(), userId, result);
        return result;
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
    public Optional<ReservationDetailResult> getReservationDetail(Long id, Long userId) {
        return reservationRepository.findById(id)
                .filter(reservation -> Objects.equals(reservation.getUserId(), userId))
                .map(this::initializeAssociations)
                .map(reservation -> new ReservationDetailResult(reservation, fetchCancellationOptions(reservation)));
    }

    /**
     * Cancels via TourVisio using the reservation's external reference, then maps the returned status
     * onto our {@link ReservationStatus} and persists it. No local status change on any failure.
     */
    @Transactional
    public CancelResult cancelReservation(Long id, Long userId, String reason, List<String> serviceIds) {
        CancelResult result = doCancelReservation(id, userId, reason, serviceIds);
        logCancelOutcome(id, userId, result);
        return result;
    }

    private CancelResult doCancelReservation(Long id, Long userId, String reason, List<String> serviceIds) {
        Optional<Reservation> found = reservationRepository.findById(id);
        // A non-owner (or missing) reservation is reported as NotFound so existence is not revealed.
        if (found.isEmpty() || !Objects.equals(found.get().getUserId(), userId)) {
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

        // The declared price is client input. beginTransaction is the first point TourVisio prices the
        // offer itself, and it is still BEFORE the purchase — so a mismatch aborts here, buying nothing.
        Optional<ConfirmationResult> priceProblem = verifyPrice(command, begin);
        if (priceProblem.isPresent()) {
            return priceProblem.get();
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

    /**
     * Compares the amount the client declared with the amount TourVisio priced the transaction at.
     *
     * <p>{@code totalAmount} arrives from the browser and is what gets written to the DB and shown in
     * "Rezervasyonlarım", so on its own it is a client assertion: a tampered request could record a
     * 1 TL booking. It can also simply be stale — the price may have moved between search and confirm.
     * Both are caught here, before commit, so nothing is bought and nothing is persisted at a wrong price.
     *
     * <p>The amount comes from {@code reservationData.reservationInfo.priceToPay} — the passenger-facing
     * total, not the agency net (see {@code TourVisioPriceExtractor}). {@code commitTransaction} itself
     * returns identifiers only, so the transaction responses are the last place to see the provider's
     * own price while the purchase can still be called off.
     *
     * <p><b>Fail-open, loudly.</b> {@code reservationData} is an untyped node, so an operation that omits
     * the pricing block leaves nothing to compare. Blocking every booking on that would take the whole
     * flow down; instead the booking proceeds and the un-verifiability is logged at WARN, so it shows up
     * rather than quietly passing as "verified".
     *
     * @return the abort outcome when the prices disagree, empty when they match or cannot be compared
     */
    private Optional<ConfirmationResult> verifyPrice(PreviewReservationCommand command,
                                                     TourVisioCallResult<TransactionResponse> begin) {
        JsonNode reservationData = begin instanceof Success<TransactionResponse> success
                && success.body() != null && success.body().body() != null
                ? success.body().body().reservationData()
                : null;

        Optional<TourVisioPrice> actual = TourVisioPriceExtractor.extract(reservationData);
        if (actual.isEmpty() || actual.get().amount() == null) {
            log.warn("Price verification unavailable: beginTransaction carried no recognisable price. "
                    + "Proceeding with the declared amount {} {}.", command.totalAmount(), command.currency());
            return Optional.empty();
        }

        BigDecimal declared = command.totalAmount();
        BigDecimal charged = actual.get().amount();
        String chargedCurrency = actual.get().currency();
        boolean amountMatches = declared != null && declared.compareTo(charged) == 0;
        boolean currencyMatches = chargedCurrency == null || chargedCurrency.equalsIgnoreCase(command.currency());

        if (amountMatches && currencyMatches) {
            return Optional.empty();
        }

        // Not necessarily an attack — a genuine price change looks identical from here — but it is
        // always a refusal to buy, and always worth a loud, durable trace. (The LogMod activity event
        // is emitted once, by logConfirmOutcome, for this and every other terminal outcome.)
        log.error("PRICE MISMATCH — declared {} {} but TourVisio priced the transaction at {} {}. "
                        + "Aborting before commit; nothing purchased. userId={}",
                declared, command.currency(), charged, chargedCurrency, command.userId());

        return Optional.of(new ConfirmationResult.PriceMismatch(declared, charged,
                chargedCurrency == null ? command.currency() : chargedCurrency));
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

        // What we record must be what was actually charged, read back from the booking itself.
        PreviewReservationCommand booked = reconcileWithBookedPrice(command, externalReservationNumber);

        Reservation reservation = entityMapper.toReservation(booked, reservationNumber, externalReservationNumber);
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
     * Reads the finalized price back from the booking itself and records THAT, not the amount we asked
     * to be charged.
     *
     * <p>{@code commitTransaction} echoes identifiers only — no pricing — so the authoritative figure for
     * a completed booking is {@code getReservationDetail}, whose body nests the same
     * {@code reservationInfo} pricing block as the transaction responses. The pre-commit check
     * ({@link #verifyPrice}) already refuses to buy at a wrong price, so this is the second half of the
     * guarantee: it closes the window between the last price we saw and the price TourVisio actually
     * committed, and it means the amount in "Rezervasyonlarım" is the amount on the booking.
     *
     * <p><b>Never fails the reservation.</b> The purchase has already happened and cannot be rolled back,
     * so a lookup that errors, times out or returns no price must not turn a successful booking into an
     * error: we fall back to the (already verified) amount and log the fact that it could not be
     * reconciled. A figure that differs is logged at ERROR — it is the one case a human should look at.
     */
    private PreviewReservationCommand reconcileWithBookedPrice(PreviewReservationCommand command,
                                                               String externalReservationNumber) {
        if (externalReservationNumber == null || externalReservationNumber.isBlank()) {
            log.warn("Booked price not reconciled: commit returned no reservation number. "
                    + "Recording the verified amount {} {}.", command.totalAmount(), command.currency());
            return command;
        }

        Optional<TourVisioPrice> booked;
        try {
            TourVisioCallResult<RawTourVisioResponse> detail =
                    bookingClient.getReservationDetail(externalReservationNumber);
            booked = detail instanceof Success<RawTourVisioResponse> success && success.body() != null
                    ? TourVisioPriceExtractor.extract(success.body().body())
                    : Optional.empty();
        } catch (RuntimeException e) {
            log.warn("Booked price not reconciled for {}: {}. Recording the verified amount {} {}.",
                    externalReservationNumber, e.getMessage(), command.totalAmount(), command.currency());
            return command;
        }

        if (booked.isEmpty() || booked.get().amount() == null) {
            log.warn("Booked price not reconciled: getReservationDetail({}) carried no price. "
                    + "Recording the verified amount {} {}.",
                    externalReservationNumber, command.totalAmount(), command.currency());
            return command;
        }

        BigDecimal charged = booked.get().amount();
        String chargedCurrency = booked.get().currency() == null ? command.currency() : booked.get().currency();
        if (command.totalAmount() != null && command.totalAmount().compareTo(charged) == 0) {
            return command;
        }

        // The booking was committed at a price we had not seen. Record the truth and flag it loudly:
        // the money moved, and only the provider's figure reconciles with what the customer is charged.
        log.error("BOOKED PRICE DIFFERS — confirmed at {} {} but TourVisio booked {} {} "
                        + "(externalReservationNumber={}). Recording TourVisio's amount.",
                command.totalAmount(), command.currency(), charged, chargedCurrency, externalReservationNumber);
        logModuleClient.logActivity("ReservationModule", "confirmReservation", maskedSummary(command),
                "WARNING", "Booked price differs: confirmed " + command.totalAmount() + " " + command.currency()
                        + ", TourVisio " + charged + " " + chargedCurrency
                        + " (externalReservationNumber=" + externalReservationNumber + ")");

        return command.withTotalAmount(charged, chargedCurrency);
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

    // =========================================================================================
    // Async activity logging (LogMod) — non-blocking, PII-safe summaries only.
    // =========================================================================================

    /**
     * Emits a non-blocking LogMod activity event for a <em>terminal</em> confirm outcome. A warning
     * awaiting a second confirm is intentionally not logged here (it is not terminal); everything else
     * is recorded as SUCCESS (persisted) or FAILED. The payload is a PII-safe summary only —
     * passenger names and contact details are never included.
     */
    private void logConfirmOutcome(PreviewReservationCommand command, Long userId, ConfirmationResult result) {
        if (result instanceof ConfirmationResult.NeedsUserConfirmation) {
            return;
        }
        if (result instanceof ConfirmationResult.Confirmed confirmed) {
            logModuleClient.logActivity("ReservationModule", "confirmReservation", maskedSummary(command),
                    "SUCCESS", "Reservation " + confirmed.reservationNumber() + " confirmed for user " + userId);
        } else {
            logModuleClient.logActivity("ReservationModule", "confirmReservation", maskedSummary(command),
                    "FAILED", "Confirm failed for user " + userId + ": " + result.getClass().getSimpleName());
        }
    }

    /** Emits a non-blocking LogMod event for a cancel outcome (a not-found/not-owned result is not logged). */
    private void logCancelOutcome(Long id, Long userId, CancelResult result) {
        if (result instanceof CancelResult.Cancelled cancelled) {
            logModuleClient.logActivity("ReservationModule", "cancelReservation", "reservationId=" + id,
                    "SUCCESS", "Reservation " + id + " cancelled by user " + userId + " -> " + cancelled.newStatus());
        } else if (!(result instanceof CancelResult.NotFound)) {
            logModuleClient.logActivity("ReservationModule", "cancelReservation", "reservationId=" + id,
                    "FAILED", "Cancel failed for user " + userId + ": " + result.getClass().getSimpleName());
        }
    }

    /** PII-safe one-line summary of a booking command for logs: product mix, amount, traveller count. */
    private String maskedSummary(PreviewReservationCommand command) {
        String products = (command.hotel() != null ? "H" : "") + (command.flight() != null ? "F" : "");
        int travellers = command.travellers() == null ? 0 : command.travellers().size();
        return "products=" + (products.isEmpty() ? "-" : products)
                + ", amount=" + command.totalAmount() + " " + command.currency()
                + ", travellers=" + travellers;
    }
}
