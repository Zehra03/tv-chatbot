package com.paximum.paxassist.reservation.pending;

import java.time.Instant;

import com.paximum.paxassist.reservation.domain.ReservationCaller;
import com.paximum.paxassist.reservation.service.command.PreviewReservationCommand;

/**
 * Frozen preview snapshot held in Redis between {@code previewReservation} and
 * {@code confirmReservation}. It captures the exact validated input the user saw in the summary, so
 * confirmation is driven purely from this snapshot — there is no re-reading of live prices/availability
 * (see the accepted-risk note in {@code ReservationService}).
 *
 * @param previewId  generated UUID; the Redis key and the handle returned to the caller
 * @param userId     the requesting user's id, or null for a guest; used for the ownership check at
 *                   confirm time
 * @param guestToken the requesting guest's opaque X-Guest-Id, or null for a logged-in user. Without
 *                   it every guest preview would be owned by "null" and any visitor holding a leaked
 *                   previewId could confirm — i.e. purchase — someone else's booking
 * @param createdAt  when the snapshot was frozen (for diagnostics; TTL is enforced by Redis)
 * @param command    the frozen, already-validated booking input
 */
public record PendingReservation(
        String previewId,
        Long userId,
        String guestToken,
        Instant createdAt,
        PreviewReservationCommand command) {

    /** The owner of this snapshot, for the confirm-time ownership check. */
    public ReservationCaller owner() {
        return new ReservationCaller(userId, guestToken);
    }
}
