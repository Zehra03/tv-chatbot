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
import com.paximum.paxassist.reservation.infrastructure.tourvisio.dto.response.TourVisioResponseHeader;
import com.paximum.paxassist.reservation.infrastructure.tourvisio.dto.response.TransactionResponse;
import com.paximum.paxassist.reservation.infrastructure.tourvisio.result.TourVisioCallResult;
import com.paximum.paxassist.reservation.pending.PendingReservation;
import com.paximum.paxassist.reservation.pending.PendingReservationStore;
import com.paximum.paxassist.reservation.recovery.OrphanedBookingRepository;
import com.paximum.paxassist.reservation.repository.ReservationRepository;
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
    void previewReservation_doesNotTriggerTourVisioAndSavesToStore() {
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
                123L, "EUR", new BigDecimal("1500.00"), "en-US", "John Doe",
                "Notes", "Agency-1", List.of("OFFER-1"), List.of(),
                List.of(mockTraveller), null, mockHotel, null);

        when(pendingStore.previewTtl()).thenReturn(java.time.Duration.ofMinutes(10));

        // When
        ReservationPreview preview = reservationService.previewReservation(command);

        // Then
        assertThat(preview.productType()).isEqualTo(ProductType.HOTEL);
        assertThat(preview.previewId()).isNotBlank();
        
        verify(pendingStore).savePreview(any(PendingReservation.class));
        verifyNoInteractions(bookingClient); // CRITICAL: TourVisio MUST NOT be called
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
                userId, "EUR", new BigDecimal("1500.00"), "en-US", "Lead Guest",
                "Notes", "Agency-1", List.of("OFFER-1"), List.of(),
                List.of(mockTraveller), null, mockHotel, null);

        PendingReservation pending = new PendingReservation(previewId, userId, Instant.now(), command);
        
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
        ConfirmationResult result = reservationService.confirmReservation(previewId, userId);

        // Then
        assertThat(result).isInstanceOf(ConfirmationResult.Confirmed.class);
        verify(reservationRepository).save(reservation);
        verify(logModuleClient).logActivity(eq("ReservationModule"), eq("confirmReservation"), anyString(), eq("SUCCESS"), anyString());
    }

    @Test
    void confirmReservation_unownedPreview_failsImmediately() {
        // Given
        String previewId = "preview-123";
        PendingReservation pending = new PendingReservation(previewId, 999L, Instant.now(), null); // Belongs to user 999
        when(pendingStore.peekPreview(previewId)).thenReturn(Optional.of(pending));

        // When
        ConfirmationResult result = reservationService.confirmReservation(previewId, 123L);

        // Then
        assertThat(result).isInstanceOf(ConfirmationResult.OwnershipMismatch.class);
        verifyNoInteractions(bookingClient);
        verifyNoInteractions(reservationRepository);
    }
}
