package com.paximum.paxassist.reservation.recovery;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Persistence for {@link OrphanedBooking} fallback records.
 *
 * <p>{@link #findByReconciledFalseOrderByCreatedAtAsc()} is what makes these rows readable at all —
 * previously the table was written to ({@code ReservationService#handleOrphanedBooking}) but nothing
 * in the codebase ever queried it back, so a real TourVisio purchase with no local reservation could
 * sit unnoticed indefinitely. Oldest-first, since an unresolved orphan only gets more urgent with age.
 * Backed by the {@code idx_orphaned_bookings_reconciled} index (V3) on {@code (reconciled, created_at)}.
 */
public interface OrphanedBookingRepository extends JpaRepository<OrphanedBooking, Long> {

    List<OrphanedBooking> findByReconciledFalseOrderByCreatedAtAsc();
}
