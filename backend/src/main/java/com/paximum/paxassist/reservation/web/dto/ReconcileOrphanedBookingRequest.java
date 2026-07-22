package com.paximum.paxassist.reservation.web.dto;

import jakarta.validation.constraints.Size;

/**
 * Body for {@code PATCH /api/v1/reservations/orphaned/{id}/reconcile}. {@code note} is optional but
 * strongly expected in practice — {@code reconciled=true} alone leaves no trace of how a real
 * TourVisio purchase was matched back to a reservation.
 */
public record ReconcileOrphanedBookingRequest(@Size(max = 2000) String note) {
}
