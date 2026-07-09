package com.paximum.paxassist.reservation.pending;

import java.time.Instant;
import java.util.List;

import com.paximum.paxassist.reservation.service.command.PreviewReservationCommand;

/**
 * Second-phase pending state: the transaction has been opened on TourVisio (begin → add →
 * setReservationInfo done) but {@code setReservationInfo} returned a
 * {@code messageType == 4} warning (e.g. {@code DuplicateReservationFound}), so we must NOT commit
 * until the user explicitly confirms a second time.
 *
 * <p>Held in Redis under a distinct {@code confirmationToken}; claiming it atomically (GETDEL) is
 * what prevents a duplicate second-confirm from committing twice.
 *
 * @param confirmationToken generated UUID handle returned to the caller for the second confirm
 * @param userId            owner, re-checked on the second confirm
 * @param transactionId     the open TourVisio transaction to commit
 * @param command           the original frozen input, still needed to persist entities after commit
 * @param warnings          the TourVisio warning message(s) the user is acknowledging
 */
public record AwaitingCommit(
        String confirmationToken,
        Long userId,
        String transactionId,
        Instant createdAt,
        PreviewReservationCommand command,
        List<String> warnings) {
}
