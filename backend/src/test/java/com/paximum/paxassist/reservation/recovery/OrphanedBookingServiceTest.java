package com.paximum.paxassist.reservation.recovery;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.paximum.paxassist.common.log.ActivityLog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Before this class (and its controller) existed, an orphaned booking was written once by
 * {@code ReservationService#handleOrphanedBooking} and then never read by anything — no endpoint, no
 * job. These tests pin the read-back and reconcile behavior that closes that gap.
 */
@ExtendWith(MockitoExtension.class)
class OrphanedBookingServiceTest {

    @Mock private OrphanedBookingRepository repository;
    @Mock private ActivityLog activityLog;

    private OrphanedBookingService service;

    private OrphanedBookingService service() {
        return new OrphanedBookingService(repository, activityLog);
    }

    @Test
    void listUnreconciled_delegatesToTheIndexedRepositoryQuery() {
        service = service();
        OrphanedBooking booking = booking(1L, false);
        when(repository.findByReconciledFalseOrderByCreatedAtAsc()).thenReturn(List.of(booking));

        List<OrphanedBooking> result = service.listUnreconciled();

        assertThat(result).containsExactly(booking);
    }

    @Test
    void reconcile_marksReconciledAndRecordsTheOperatorNote() {
        service = service();
        OrphanedBooking booking = booking(7L, false);
        when(repository.findById(7L)).thenReturn(Optional.of(booking));
        when(repository.save(any(OrphanedBooking.class))).thenAnswer(inv -> inv.getArgument(0));

        Optional<OrphanedBooking> result = service.reconcile(7L, "Reservation #42 created manually.", 99L);

        assertThat(result).isPresent();
        assertThat(result.get().isReconciled()).isTrue();
        assertThat(result.get().getResolutionNote()).isEqualTo("Reservation #42 created manually.");
    }

    @Test
    void reconcile_blankNoteIsNotRecorded() {
        service = service();
        OrphanedBooking booking = booking(8L, false);
        when(repository.findById(8L)).thenReturn(Optional.of(booking));
        when(repository.save(any(OrphanedBooking.class))).thenAnswer(inv -> inv.getArgument(0));

        Optional<OrphanedBooking> result = service.reconcile(8L, "   ", 99L);

        assertThat(result.get().isReconciled()).isTrue();
        assertThat(result.get().getResolutionNote()).isNull();
    }

    @Test
    void reconcile_unknownIdReturnsEmptyAndNeverSaves() {
        service = service();
        when(repository.findById(404L)).thenReturn(Optional.empty());

        Optional<OrphanedBooking> result = service.reconcile(404L, "note", 1L);

        assertThat(result).isEmpty();
        verify(repository, never()).save(any());
    }

    @Test
    void reconcile_emitsAnActivityLogEntry() {
        service = service();
        OrphanedBooking booking = booking(3L, false);
        when(repository.findById(3L)).thenReturn(Optional.of(booking));
        when(repository.save(any(OrphanedBooking.class))).thenAnswer(inv -> inv.getArgument(0));

        service.reconcile(3L, "fixed", 55L);

        ArgumentCaptor<String> message = ArgumentCaptor.forClass(String.class);
        verify(activityLog).logActivity(
                org.mockito.ArgumentMatchers.eq("ReservationModule"),
                org.mockito.ArgumentMatchers.eq("reconcileOrphanedBooking"),
                any(), org.mockito.ArgumentMatchers.eq("SUCCESS"), message.capture());
        assertThat(message.getValue()).contains("3").contains("55");
    }

    private OrphanedBooking booking(Long id, boolean reconciled) {
        OrphanedBooking booking = new OrphanedBooking();
        booking.setId(id);
        booking.setExternalReservationNumber("RC" + id);
        booking.setIntendedReservationNumber("PAX-" + id);
        booking.setTransactionId("txn-" + id);
        booking.setUserId(42L);
        booking.setLeadGuestName("Zehra Yılmaz");
        booking.setTotalAmount(new BigDecimal("1234.56"));
        booking.setCurrency("EUR");
        booking.setFailureReason("java.sql.SQLException: connection reset");
        booking.setReconciled(reconciled);
        return booking;
    }
}
