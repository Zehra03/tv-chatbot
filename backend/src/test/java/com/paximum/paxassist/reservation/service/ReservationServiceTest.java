package com.paximum.paxassist.reservation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.paximum.paxassist.common.log.LogModuleClient;
import com.paximum.paxassist.reservation.domain.ProductType;
import com.paximum.paxassist.reservation.domain.Reservation;
import com.paximum.paxassist.reservation.infrastructure.tourvisio.client.TourVisioBookingClient;
import com.paximum.paxassist.reservation.infrastructure.tourvisio.dto.request.BeginTransactionWithOfferRequest;
import com.paximum.paxassist.reservation.infrastructure.tourvisio.dto.request.CommitTransactionRequest;
import com.paximum.paxassist.reservation.infrastructure.tourvisio.dto.request.SetReservationInfoRequest;
import com.paximum.paxassist.reservation.infrastructure.tourvisio.dto.response.CommitTransactionResponse;
import com.paximum.paxassist.reservation.infrastructure.tourvisio.dto.response.RawTourVisioResponse;
import com.paximum.paxassist.reservation.infrastructure.tourvisio.dto.response.TourVisioResponseHeader;
import com.paximum.paxassist.reservation.infrastructure.tourvisio.dto.response.TransactionResponse;
import com.paximum.paxassist.reservation.infrastructure.tourvisio.result.TourVisioCallResult;
import com.paximum.paxassist.reservation.pending.PendingReservation;
import com.paximum.paxassist.reservation.pending.PendingReservationStore;
import com.paximum.paxassist.reservation.recovery.OrphanedBookingRepository;
import com.paximum.paxassist.reservation.repository.ReservationRepository;
import com.paximum.paxassist.reservation.domain.ReservationCaller;
import com.paximum.paxassist.reservation.service.command.PreviewReservationCommand;

@ExtendWith(MockitoExtension.class)
class ReservationServiceTest {

    @Mock private PendingReservationStore pendingStore;
    @Mock private TourVisioBookingClient bookingClient;
    @Mock private ReservationTourVisioRequestMapper requestMapper;
    @Mock private ReservationEntityMapper entityMapper;
    @Mock private ReservationRepository reservationRepository;
    @Mock private OrphanedBookingRepository orphanedBookingRepository;
    @Mock private LogModuleClient logModuleClient;

    @InjectMocks
    private ReservationService reservationService;

    @Test
    void previewReservation_repricesTheOfferAndFreezesTheSnapshot() {
        // The preview now asks TourVisio to price the offer (K21) — but it still writes nothing to the DB
        // and still buys nothing. See previewReservation_neverBuysAnything for that invariant.
        PreviewReservationCommand command = bookingFor(new BigDecimal("1500.00"));
        when(requestMapper.toBeginRequest(command))
                .thenReturn(new BeginTransactionWithOfferRequest(List.of(), "EUR", "en-US"));
        when(bookingClient.beginTransactionWithOffer(any()))
                .thenReturn(new TourVisioCallResult.Success<>(beginResponsePricedAt("1500.00")));
        when(pendingStore.previewTtl()).thenReturn(java.time.Duration.ofMinutes(10));

        ReservationPreview preview = ((PreviewResult.Priced)
                reservationService.previewReservation(command)).preview();

        assertThat(preview.productType()).isEqualTo(ProductType.HOTEL);
        assertThat(preview.previewId()).isNotBlank();

        verify(pendingStore).savePreview(any(PendingReservation.class));
        verifyNoInteractions(reservationRepository);
    }

    // =============================================================================================
    // K21 — the preview re-validates price and availability against TourVisio.
    // =============================================================================================

    @Test
    void previewReservation_soldOutOffer_stopsTheFlowInsteadOfFreezingIt() {
        // TourVisio refuses to price the offer: it is gone. The user finds out here, not after confirm.
        PreviewReservationCommand command = bookingFor(new BigDecimal("1500.00"));
        when(requestMapper.toBeginRequest(command))
                .thenReturn(new BeginTransactionWithOfferRequest(List.of(), "EUR", "en-US"));
        when(bookingClient.beginTransactionWithOffer(any())).thenReturn(
                new TourVisioCallResult.BusinessFailure<>(new TourVisioResponseHeader("req-1", false, List.of())));

        PreviewResult result = reservationService.previewReservation(command);

        assertThat(result).isInstanceOf(PreviewResult.Unavailable.class);
        // Nothing is frozen: a preview the user cannot actually book must not exist.
        verify(pendingStore, org.mockito.Mockito.never()).savePreview(any());
    }

    @Test
    void previewReservation_providerDown_freezesNothingRatherThanAnUncheckedPrice() {
        PreviewReservationCommand command = bookingFor(new BigDecimal("1500.00"));
        when(requestMapper.toBeginRequest(command))
                .thenReturn(new BeginTransactionWithOfferRequest(List.of(), "EUR", "en-US"));
        when(bookingClient.beginTransactionWithOffer(any()))
                .thenReturn(new TourVisioCallResult.TechnicalFailure<TransactionResponse>("read timed out", null));

        PreviewResult result = reservationService.previewReservation(command);

        assertThat(result).isInstanceOf(PreviewResult.ProviderUnavailable.class);
        verify(pendingStore, org.mockito.Mockito.never()).savePreview(any());
    }

    @Test
    void previewReservation_priceMovedSinceSearch_showsBothAmountsAndFreezesTheLiveOne() {
        // The search said 1500; TourVisio now says 1750. The user must see both — and whatever they
        // then confirm must be the REAL price, not the stale one.
        PreviewReservationCommand command = bookingFor(new BigDecimal("1500.00"));
        when(requestMapper.toBeginRequest(command))
                .thenReturn(new BeginTransactionWithOfferRequest(List.of(), "EUR", "en-US"));
        when(bookingClient.beginTransactionWithOffer(any()))
                .thenReturn(new TourVisioCallResult.Success<>(beginResponsePricedAt("1750.00")));
        when(pendingStore.previewTtl()).thenReturn(java.time.Duration.ofMinutes(15));

        PreviewResult result = reservationService.previewReservation(command);

        ReservationPreview preview = ((PreviewResult.Priced) result).preview();
        assertThat(preview.priceChanged()).isTrue();
        assertThat(preview.previousAmount()).isEqualByComparingTo(new BigDecimal("1500.00"));
        assertThat(preview.totalAmount()).isEqualByComparingTo(new BigDecimal("1750.00"));

        // The frozen snapshot carries the live price, so the DB can never record the stale figure.
        org.mockito.ArgumentCaptor<PendingReservation> captor =
                org.mockito.ArgumentCaptor.forClass(PendingReservation.class);
        verify(pendingStore).savePreview(captor.capture());
        assertThat(captor.getValue().command().totalAmount()).isEqualByComparingTo(new BigDecimal("1750.00"));
    }

    @Test
    void previewReservation_unchangedPrice_isNotFlaggedAsAChange() {
        PreviewReservationCommand command = bookingFor(new BigDecimal("1500.00"));
        when(requestMapper.toBeginRequest(command))
                .thenReturn(new BeginTransactionWithOfferRequest(List.of(), "EUR", "en-US"));
        when(bookingClient.beginTransactionWithOffer(any()))
                .thenReturn(new TourVisioCallResult.Success<>(beginResponsePricedAt("1500.00")));
        when(pendingStore.previewTtl()).thenReturn(java.time.Duration.ofMinutes(15));

        ReservationPreview preview = ((PreviewResult.Priced)
                reservationService.previewReservation(command)).preview();

        assertThat(preview.priceChanged()).isFalse();
        assertThat(preview.previousAmount()).isNull();
    }

    @Test
    void previewReservation_summaryCarriesWhatTheUserIsAgreeingTo() {
        // The summary screen must state the product, its dates, the party size, the price, the currency
        // and that it is available — not just a number to click "confirm" on.
        PreviewReservationCommand command = bookingFor(new BigDecimal("1500.00"));
        when(requestMapper.toBeginRequest(command))
                .thenReturn(new BeginTransactionWithOfferRequest(List.of(), "EUR", "en-US"));
        when(bookingClient.beginTransactionWithOffer(any()))
                .thenReturn(new TourVisioCallResult.Success<>(beginResponsePricedAt("1500.00")));
        when(pendingStore.previewTtl()).thenReturn(java.time.Duration.ofMinutes(15));

        ReservationPreview preview = ((PreviewResult.Priced)
                reservationService.previewReservation(command)).preview();

        assertThat(preview.available()).isTrue();
        assertThat(preview.currency()).isEqualTo("EUR");
        assertThat(preview.totalAmount()).isEqualByComparingTo(new BigDecimal("1500.00"));

        ReservationPreview.Hotel hotel = preview.hotel();
        assertThat(hotel.hotelName()).isEqualTo("Hotel A");
        assertThat(hotel.checkIn()).isEqualTo(java.time.LocalDate.now().plusDays(30));
        assertThat(hotel.checkOut()).isEqualTo(java.time.LocalDate.now().plusDays(32));
        assertThat(hotel.nights()).isEqualTo(2);
        assertThat(hotel.rooms()).isEqualTo((short) 1);
        assertThat(hotel.adults()).isEqualTo((short) 1);
        assertThat(hotel.children()).isEqualTo((short) 0);
        assertThat(preview.passengerNames()).containsExactly("John Doe");
        assertThat(preview.flight()).isNull();
    }

    @Test
    void previewReservation_neverBuysAnything() {
        // The invariant the re-pricing must not break: beginTransaction prices, only commit buys.
        PreviewReservationCommand command = bookingFor(new BigDecimal("1500.00"));
        when(requestMapper.toBeginRequest(command))
                .thenReturn(new BeginTransactionWithOfferRequest(List.of(), "EUR", "en-US"));
        when(bookingClient.beginTransactionWithOffer(any()))
                .thenReturn(new TourVisioCallResult.Success<>(beginResponsePricedAt("1500.00")));
        when(pendingStore.previewTtl()).thenReturn(java.time.Duration.ofMinutes(15));

        reservationService.previewReservation(command);

        verify(bookingClient, org.mockito.Mockito.never()).commitTransaction(any());
        verify(bookingClient, org.mockito.Mockito.never()).setReservationInfo(any());
        verifyNoInteractions(reservationRepository);
    }

    @Test
    void confirmReservation_success_savesToDbAndLogsActivity() {
        // Given
        String previewId = "preview-123";
        Long userId = 123L;
        PreviewReservationCommand.Hotel mockHotel = new PreviewReservationCommand.Hotel(
                "Hotel A", "Region", (short) 4, "BB",
                java.time.LocalDate.now(), java.time.LocalDate.now().plusDays(2),
                (short) 1, (short) 1, (short) 0, "TR", new BigDecimal("1500.00"), "EUR"
        );

        PreviewReservationCommand.Traveller mockTraveller = new PreviewReservationCommand.Traveller(
                "1", "John", "Doe", com.paximum.paxassist.reservation.domain.PassengerType.ADULT,
                30, "TR", "test@test.com", "555", true, 1, 1, java.time.LocalDate.of(1990, 1, 1),
                "123", null, null, null
        );

        PreviewReservationCommand command = new PreviewReservationCommand(
                userId, null, "EUR", new BigDecimal("1500.00"), "en-US", "Lead Guest",
                "Notes", "Agency-1", List.of("OFFER-1"), List.of(),
                List.of(mockTraveller), null, mockHotel, null);

        PendingReservation pending = new PendingReservation(previewId, userId, null, Instant.now(), command);
        
        when(pendingStore.peekPreview(previewId)).thenReturn(Optional.of(pending));
        when(pendingStore.claimPreview(previewId)).thenReturn(Optional.of(pending));

        // Mock TourVisio Flow
        TourVisioResponseHeader header = new TourVisioResponseHeader("req-123", true, List.of());
        TransactionResponse.Body txnBody = new TransactionResponse.Body("txn-id", "2026-07-08T12:00:00Z", null, 1, 1);
        TransactionResponse txnResponse = new TransactionResponse(header, txnBody);

        when(requestMapper.toBeginRequest(command)).thenReturn(new BeginTransactionWithOfferRequest(List.of(), "EUR", "en-US"));
        when(bookingClient.beginTransactionWithOffer(any())).thenReturn(new TourVisioCallResult.Success<>(txnResponse));
        
        when(requestMapper.toAddServicesRequest(command, "txn-id")).thenReturn(Optional.empty());
        
        when(requestMapper.toSetReservationInfoRequest(command, "txn-id")).thenReturn(new SetReservationInfoRequest("txn-id", List.of(), null, null, null));
        when(bookingClient.setReservationInfo(any())).thenReturn(new TourVisioCallResult.Success<>(txnResponse));

        CommitTransactionResponse.Body commitBody = new CommitTransactionResponse.Body("TV-RES-1", "ENC-1", "txn-id");
        CommitTransactionResponse commitResponse = new CommitTransactionResponse(header, commitBody);
        
        when(requestMapper.toCommitRequest(command, "txn-id")).thenReturn(new CommitTransactionRequest("txn-id", null));
        when(bookingClient.commitTransaction(any())).thenReturn(new TourVisioCallResult.Success<>(commitResponse));

        Reservation reservation = new Reservation();
        reservation.setId(99L);
        reservation.setReservationNumber("LOCAL-RES-1");
        
        when(entityMapper.toReservation(eq(command), anyString(), eq("TV-RES-1"))).thenReturn(reservation);
        when(reservationRepository.save(reservation)).thenReturn(reservation);

        // When
        ConfirmationResult result =
                reservationService.confirmReservation(previewId, ReservationCaller.authenticated(userId));

        // Then
        assertThat(result).isInstanceOf(ConfirmationResult.Confirmed.class);
        verify(reservationRepository).save(reservation);
        verify(logModuleClient).logActivity(eq("ReservationModule"), eq("confirmReservation"), anyString(), eq("SUCCESS"), anyString());
    }

    // =============================================================================================
    // Price verification (the declared amount is client input) and preview recovery.
    // =============================================================================================

    private PreviewReservationCommand bookingFor(BigDecimal declaredAmount) {
        PreviewReservationCommand.Hotel hotel = new PreviewReservationCommand.Hotel(
                "Hotel A", "Region", (short) 4, "BB",
                java.time.LocalDate.now().plusDays(30), java.time.LocalDate.now().plusDays(32),
                (short) 1, (short) 1, (short) 0, "TR", declaredAmount, "EUR");
        PreviewReservationCommand.Traveller traveller = new PreviewReservationCommand.Traveller(
                "1", "John", "Doe", com.paximum.paxassist.reservation.domain.PassengerType.ADULT,
                30, "TR", "test@test.com", "555", true, 1, 1, java.time.LocalDate.of(1990, 1, 1),
                "123", null, null, null);
        return new PreviewReservationCommand(123L, null, "EUR", declaredAmount, "en-US", "John Doe",
                null, null, List.of("OFFER-1"), List.of(), List.of(traveller), null, hotel, null);
    }

    /**
     * A beginTransaction response carrying TourVisio's own pricing, in the confirmed shape: the
     * passenger total under {@code reservationInfo.priceToPay}, with the agency net (10% lower)
     * beside it — the value that must NOT be compared against the user's declared amount.
     */
    private TransactionResponse beginResponsePricedAt(String amount) {
        com.fasterxml.jackson.databind.JsonNode reservationData = null;
        if (amount != null) {
            java.math.BigDecimal agencyNet = new java.math.BigDecimal(amount)
                    .multiply(new java.math.BigDecimal("0.90"));
            try {
                reservationData = new com.fasterxml.jackson.databind.ObjectMapper().readTree("""
                        {
                          "reservationInfo": {
                            "priceToPay": {"amount": %s, "currency": "EUR"},
                            "totalPrice": {"amount": %s, "currency": "EUR"},
                            "agencyPriceToPay": {"amount": %s, "currency": "EUR"}
                          }
                        }
                        """.formatted(amount, amount, agencyNet));
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        }
        return new TransactionResponse(
                new TourVisioResponseHeader("req-1", true, List.of()),
                new TransactionResponse.Body("txn-id", "2026-08-01T12:00:00Z", reservationData, 1, 1));
    }

    /** Claims a preview for user 123 and stubs beginTransaction to return the given response. */
    private PendingReservation givenClaimedPreviewWithBegin(PreviewReservationCommand command,
                                                            TourVisioCallResult<TransactionResponse> begin) {
        PendingReservation pending = new PendingReservation("preview-123", 123L, null, Instant.now(), command);
        when(pendingStore.peekPreview("preview-123")).thenReturn(Optional.of(pending));
        when(pendingStore.claimPreview("preview-123")).thenReturn(Optional.of(pending));
        when(requestMapper.toBeginRequest(command))
                .thenReturn(new BeginTransactionWithOfferRequest(List.of(), "EUR", "en-US"));
        when(bookingClient.beginTransactionWithOffer(any())).thenReturn(begin);
        return pending;
    }

    @Test
    void confirmReservation_declaredPriceBelowTheRealOne_abortsBeforeBuyingAnything() {
        // The classic tampered request: a 1 EUR booking for a 1500 EUR offer. It must never be bought,
        // and it must never be persisted at the fake amount.
        PreviewReservationCommand command = bookingFor(new BigDecimal("1.00"));
        givenClaimedPreviewWithBegin(command,
                new TourVisioCallResult.Success<>(beginResponsePricedAt("1500.00")));

        ConfirmationResult result = reservationService.confirmReservation("preview-123", ReservationCaller.authenticated(123L));

        assertThat(result).isInstanceOf(ConfirmationResult.PriceMismatch.class);
        ConfirmationResult.PriceMismatch mismatch = (ConfirmationResult.PriceMismatch) result;
        assertThat(mismatch.declaredAmount()).isEqualByComparingTo(new BigDecimal("1.00"));
        assertThat(mismatch.actualAmount()).isEqualByComparingTo(new BigDecimal("1500.00"));

        // The point of aborting at beginTransaction: no purchase, no DB row.
        verify(bookingClient, org.mockito.Mockito.never()).commitTransaction(any());
        verifyNoInteractions(reservationRepository);
        verify(logModuleClient).logActivity(eq("ReservationModule"), eq("confirmReservation"), anyString(),
                eq("FAILED"), anyString());
    }

    @Test
    void confirmReservation_priceChangedSinceSearch_isTheSameAbort() {
        // Indistinguishable from tampering at this point, and the answer is the same: buy nothing.
        PreviewReservationCommand command = bookingFor(new BigDecimal("1500.00"));
        givenClaimedPreviewWithBegin(command,
                new TourVisioCallResult.Success<>(beginResponsePricedAt("1750.00")));

        ConfirmationResult result = reservationService.confirmReservation("preview-123", ReservationCaller.authenticated(123L));

        assertThat(result).isInstanceOf(ConfirmationResult.PriceMismatch.class);
        verify(bookingClient, org.mockito.Mockito.never()).commitTransaction(any());
        // A stale price must NOT be handed back for a retry — the user has to preview the new one.
        verify(pendingStore, org.mockito.Mockito.never()).restorePreview(any());
    }

    @Test
    void confirmReservation_matchingPrice_proceedsToCommit() {
        PreviewReservationCommand command = bookingFor(new BigDecimal("1500.00"));
        givenClaimedPreviewWithBegin(command,
                new TourVisioCallResult.Success<>(beginResponsePricedAt("1500.00")));

        when(requestMapper.toAddServicesRequest(command, "txn-id")).thenReturn(Optional.empty());
        when(requestMapper.toSetReservationInfoRequest(command, "txn-id"))
                .thenReturn(new SetReservationInfoRequest("txn-id", List.of(), null, null, null));
        when(bookingClient.setReservationInfo(any()))
                .thenReturn(new TourVisioCallResult.Success<>(beginResponsePricedAt("1500.00")));
        when(requestMapper.toCommitRequest(command, "txn-id"))
                .thenReturn(new CommitTransactionRequest("txn-id", null));
        when(bookingClient.commitTransaction(any())).thenReturn(new TourVisioCallResult.Success<>(
                new CommitTransactionResponse(new TourVisioResponseHeader("req-1", true, List.of()),
                        new CommitTransactionResponse.Body("TV-RES-1", "ENC-1", "txn-id"))));
        Reservation reservation = new Reservation();
        reservation.setId(99L);
        when(entityMapper.toReservation(eq(command), anyString(), eq("TV-RES-1"))).thenReturn(reservation);
        when(reservationRepository.save(reservation)).thenReturn(reservation);

        ConfirmationResult result = reservationService.confirmReservation("preview-123", ReservationCaller.authenticated(123L));

        assertThat(result).isInstanceOf(ConfirmationResult.Confirmed.class);
        verify(bookingClient).commitTransaction(any());
    }

    // =============================================================================================
    // Post-commit reconciliation: what we record must be what the booking was actually charged.
    // commitTransaction echoes identifiers only, so the price is read back via getReservationDetail.
    // =============================================================================================

    /** Drives a successful confirm and returns the command the entity was actually built from. */
    private PreviewReservationCommand confirmAndCaptureTheRecordedCommand(
            PreviewReservationCommand command, TourVisioCallResult<RawTourVisioResponse> detail) {
        givenClaimedPreviewWithBegin(command,
                new TourVisioCallResult.Success<>(beginResponsePricedAt("1500.00")));
        when(requestMapper.toAddServicesRequest(command, "txn-id")).thenReturn(Optional.empty());
        when(requestMapper.toSetReservationInfoRequest(command, "txn-id"))
                .thenReturn(new SetReservationInfoRequest("txn-id", List.of(), null, null, null));
        when(bookingClient.setReservationInfo(any()))
                .thenReturn(new TourVisioCallResult.Success<>(beginResponsePricedAt("1500.00")));
        when(requestMapper.toCommitRequest(command, "txn-id"))
                .thenReturn(new CommitTransactionRequest("txn-id", null));
        when(bookingClient.commitTransaction(any())).thenReturn(new TourVisioCallResult.Success<>(
                new CommitTransactionResponse(new TourVisioResponseHeader("req-1", true, List.of()),
                        new CommitTransactionResponse.Body("TV-RES-1", "ENC-1", "txn-id"))));
        when(bookingClient.getReservationDetail("TV-RES-1")).thenReturn(detail);

        Reservation reservation = new Reservation();
        reservation.setId(99L);
        org.mockito.ArgumentCaptor<PreviewReservationCommand> captor =
                org.mockito.ArgumentCaptor.forClass(PreviewReservationCommand.class);
        when(entityMapper.toReservation(captor.capture(), anyString(), eq("TV-RES-1"))).thenReturn(reservation);
        when(reservationRepository.save(reservation)).thenReturn(reservation);

        ConfirmationResult result = reservationService.confirmReservation("preview-123", ReservationCaller.authenticated(123L));
        assertThat(result).isInstanceOf(ConfirmationResult.Confirmed.class);
        return captor.getValue();
    }

    /** A getReservationDetail body carrying the finalized price, in the confirmed reservationInfo shape. */
    private TourVisioCallResult<RawTourVisioResponse> reservationDetailPricedAt(String amount) {
        try {
            com.fasterxml.jackson.databind.JsonNode body = new com.fasterxml.jackson.databind.ObjectMapper()
                    .readTree("""
                            {"reservationInfo": {"priceToPay": {"amount": %s, "currency": "EUR"}}}
                            """.formatted(amount));
            return new TourVisioCallResult.Success<>(new RawTourVisioResponse(
                    new TourVisioResponseHeader("req-1", true, List.of()), body));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    void confirmReservation_recordsThePriceTheBookingWasActuallyChargedAt() {
        // The booking committed at 1550 even though the transaction was priced at 1500. The DB — and so
        // "Rezervasyonlarım" — must show what the customer is actually charged, not what we asked for.
        PreviewReservationCommand command = bookingFor(new BigDecimal("1500.00"));

        PreviewReservationCommand recorded =
                confirmAndCaptureTheRecordedCommand(command, reservationDetailPricedAt("1550.00"));

        assertThat(recorded.totalAmount()).isEqualByComparingTo(new BigDecimal("1550.00"));
        verify(logModuleClient).logActivity(eq("ReservationModule"), eq("confirmReservation"), anyString(),
                eq("WARNING"), anyString());
    }

    @Test
    void confirmReservation_matchingBookedPrice_recordsItUnchanged() {
        PreviewReservationCommand command = bookingFor(new BigDecimal("1500.00"));

        PreviewReservationCommand recorded =
                confirmAndCaptureTheRecordedCommand(command, reservationDetailPricedAt("1500.00"));

        assertThat(recorded.totalAmount()).isEqualByComparingTo(new BigDecimal("1500.00"));
    }

    @Test
    void confirmReservation_reconciliationLookupFails_stillConfirmsTheBooking() {
        // The purchase already happened and cannot be rolled back: a failed price read-back must never
        // turn a successful booking into an error. Fall back to the amount already verified pre-commit.
        PreviewReservationCommand command = bookingFor(new BigDecimal("1500.00"));

        PreviewReservationCommand recorded = confirmAndCaptureTheRecordedCommand(command,
                new TourVisioCallResult.TechnicalFailure<RawTourVisioResponse>("read timed out", null));

        assertThat(recorded.totalAmount()).isEqualByComparingTo(new BigDecimal("1500.00"));
    }

    @Test
    void confirmReservation_reconciliationBodyHasNoPrice_stillConfirmsTheBooking() {
        PreviewReservationCommand command = bookingFor(new BigDecimal("1500.00"));

        PreviewReservationCommand recorded = confirmAndCaptureTheRecordedCommand(command,
                new TourVisioCallResult.Success<>(new RawTourVisioResponse(
                        new TourVisioResponseHeader("req-1", true, List.of()), null)));

        assertThat(recorded.totalAmount()).isEqualByComparingTo(new BigDecimal("1500.00"));
    }

    @Test
    void confirmReservation_transientTourVisioFailure_handsThePreviewBackForARetry() {
        // The atomic claim already consumed the preview. Without restoring it, the user's retry would
        // hit the duplicate guard and be told a confirm is in progress — while nothing was confirmed.
        PreviewReservationCommand command = bookingFor(new BigDecimal("1500.00"));
        PendingReservation pending = givenClaimedPreviewWithBegin(command,
                new TourVisioCallResult.TechnicalFailure<TransactionResponse>("read timed out", null));
        when(pendingStore.restorePreview(pending)).thenReturn(true);

        ConfirmationResult result = reservationService.confirmReservation("preview-123", ReservationCaller.authenticated(123L));

        assertThat(result).isInstanceOf(ConfirmationResult.TourVisioUnavailable.class);
        verify(pendingStore).restorePreview(pending);
        verifyNoInteractions(reservationRepository);
    }

    @Test
    void confirmReservation_transientFailureWithNoTtlLeft_says410NotAMisleading409() {
        PreviewReservationCommand command = bookingFor(new BigDecimal("1500.00"));
        PendingReservation pending = givenClaimedPreviewWithBegin(command,
                new TourVisioCallResult.TechnicalFailure<TransactionResponse>("read timed out", null));
        when(pendingStore.restorePreview(pending)).thenReturn(false);

        ConfirmationResult result = reservationService.confirmReservation("preview-123", ReservationCaller.authenticated(123L));

        // "Your preview expired, start again" — not "already being confirmed".
        assertThat(result).isInstanceOf(ConfirmationResult.PreviewExpired.class);
    }

    @Test
    void confirmReservation_ambiguousCommit_doesNotHandThePreviewBack() {
        // The purchase MAY exist. Restoring the preview would invite a second booking.
        PreviewReservationCommand command = bookingFor(new BigDecimal("1500.00"));
        givenClaimedPreviewWithBegin(command,
                new TourVisioCallResult.Success<>(beginResponsePricedAt("1500.00")));
        when(requestMapper.toAddServicesRequest(command, "txn-id")).thenReturn(Optional.empty());
        when(requestMapper.toSetReservationInfoRequest(command, "txn-id"))
                .thenReturn(new SetReservationInfoRequest("txn-id", List.of(), null, null, null));
        when(bookingClient.setReservationInfo(any()))
                .thenReturn(new TourVisioCallResult.Success<>(beginResponsePricedAt("1500.00")));
        when(requestMapper.toCommitRequest(command, "txn-id"))
                .thenReturn(new CommitTransactionRequest("txn-id", null));
        when(bookingClient.commitTransaction(any()))
                .thenReturn(new TourVisioCallResult.UnknownOutcome<CommitTransactionResponse>("TV-99", "timed out after commit", null));

        ConfirmationResult result = reservationService.confirmReservation("preview-123", ReservationCaller.authenticated(123L));

        assertThat(result).isInstanceOf(ConfirmationResult.CommitOutcomeUnknown.class);
        verify(pendingStore, org.mockito.Mockito.never()).restorePreview(any());
    }

    @Test
    void confirmReservation_unownedPreview_failsImmediately() {
        // Given
        String previewId = "preview-123";
        PendingReservation pending = new PendingReservation(previewId, 999L, null, Instant.now(), null); // Belongs to user 999
        when(pendingStore.peekPreview(previewId)).thenReturn(Optional.of(pending));

        // When
        ConfirmationResult result = reservationService.confirmReservation(previewId, ReservationCaller.authenticated(123L));

        // Then
        assertThat(result).isInstanceOf(ConfirmationResult.OwnershipMismatch.class);
        verifyNoInteractions(bookingClient);
        verifyNoInteractions(reservationRepository);
    }

    @Test
    void confirmReservation_anotherGuestsPreview_isNotOwnedJustBecauseBothLackAUserId() {
        // The reason guest ownership is a token and not a null user id: with an id-only check both
        // guests are "null", null equals null, and any visitor holding a leaked previewId could
        // confirm — i.e. actually purchase — someone else's booking.
        String previewId = "preview-123";
        PendingReservation pending =
                new PendingReservation(previewId, null, "guest-owner", Instant.now(), null);
        when(pendingStore.peekPreview(previewId)).thenReturn(Optional.of(pending));

        ConfirmationResult result =
                reservationService.confirmReservation(previewId, ReservationCaller.guest("guest-intruder"));

        assertThat(result).isInstanceOf(ConfirmationResult.OwnershipMismatch.class);
        verifyNoInteractions(bookingClient);
        verifyNoInteractions(reservationRepository);
    }

    @Test
    void confirmReservation_ownGuestPreview_passesTheOwnershipCheck() {
        // The other half of the rule: the browser that created the preview must still be able to buy.
        String previewId = "preview-123";
        PendingReservation pending =
                new PendingReservation(previewId, null, "guest-owner", Instant.now(), null);
        when(pendingStore.peekPreview(previewId)).thenReturn(Optional.of(pending));
        when(pendingStore.claimPreview(previewId)).thenReturn(Optional.empty());

        ConfirmationResult result =
                reservationService.confirmReservation(previewId, ReservationCaller.guest("guest-owner"));

        // It got past ownership and on to the atomic claim (which this test leaves unclaimable) —
        // the point being that it is NOT an OwnershipMismatch.
        assertThat(result).isInstanceOf(ConfirmationResult.DuplicateInProgress.class);
    }

    @Test
    void lookupReservation_withABlankSurname_neverReachesTheDatabase() {
        // An empty surname must not behave like a wildcard on a publicly reachable lookup.
        assertThat(reservationService.lookupReservation("PAX-20260721-ABC123", "  ")).isEmpty();
        assertThat(reservationService.lookupReservation("  ", "Yılmaz")).isEmpty();
        verifyNoInteractions(reservationRepository);
    }
}
