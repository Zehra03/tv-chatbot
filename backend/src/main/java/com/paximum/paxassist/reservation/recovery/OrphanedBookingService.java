package com.paximum.paxassist.reservation.recovery;

import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.paximum.paxassist.common.log.ActivityLog;

/**
 * Read-back and manual-reconciliation for {@link OrphanedBooking} rows.
 *
 * <p>Before this class existed, {@code ReservationService#handleOrphanedBooking} wrote a durable
 * fallback row on a commit-succeeded/persist-failed booking, but nothing ever read it back — no
 * endpoint, no scheduled job. A real TourVisio purchase with no local {@code Reservation} could sit
 * in the table unnoticed indefinitely: the customer was charged and had no visible reservation, and
 * no one — support included — had a way to find out short of querying the database by hand.
 *
 * <p>This is deliberately a small, separate service (not folded into {@link
 * com.paximum.paxassist.reservation.service.ReservationService}) — it is an operator/support tool over
 * a standalone recovery table, not part of the customer-facing preview/confirm flow.
 */
@Service
public class OrphanedBookingService {

    private static final Logger log = LoggerFactory.getLogger(OrphanedBookingService.class);

    private final OrphanedBookingRepository repository;
    private final ActivityLog activityLog;

    public OrphanedBookingService(OrphanedBookingRepository repository, ActivityLog activityLog) {
        this.repository = repository;
        this.activityLog = activityLog;
    }

    /** Unresolved orphaned bookings, oldest first — the operator queue. */
    @Transactional(readOnly = true)
    public List<OrphanedBooking> listUnreconciled() {
        return repository.findByReconciledFalseOrderByCreatedAtAsc();
    }

    /** Every orphaned booking regardless of state, newest first — for auditing what was already resolved. */
    @Transactional(readOnly = true)
    public List<OrphanedBooking> listAll() {
        return repository.findAll(org.springframework.data.domain.Sort.by(
                org.springframework.data.domain.Sort.Direction.DESC, "createdAt"));
    }

    /**
     * Marks an orphaned booking reconciled once an operator has manually verified/recreated the
     * reservation. {@code note} should say how (e.g. "Reservation #4821 created manually, verified
     * against TourVisio external ref RC00123456"), since {@code reconciled=true} alone leaves no trace
     * of what was actually done.
     *
     * @return empty when no such id exists (controller maps this to 404)
     */
    @Transactional
    public Optional<OrphanedBooking> reconcile(Long id, String note, Long resolvedByUserId) {
        Optional<OrphanedBooking> found = repository.findById(id);
        if (found.isEmpty()) {
            return Optional.empty();
        }
        OrphanedBooking booking = found.get();
        boolean alreadyReconciled = booking.isReconciled();
        booking.setReconciled(true);
        if (note != null && !note.isBlank()) {
            booking.setResolutionNote(note.trim());
        }
        OrphanedBooking saved = repository.save(booking);

        if (alreadyReconciled) {
            log.info("Orphaned booking {} re-marked reconciled by user {} (was already reconciled).",
                    id, resolvedByUserId);
        } else {
            log.info("Orphaned booking {} (externalReservationNumber={}) reconciled by user {}.",
                    id, saved.getExternalReservationNumber(), resolvedByUserId);
        }
        activityLog.logActivity("ReservationModule", "reconcileOrphanedBooking",
                "orphanedBookingId=" + id, "SUCCESS",
                "Orphaned booking " + id + " marked reconciled by user " + resolvedByUserId);
        return Optional.of(saved);
    }
}
