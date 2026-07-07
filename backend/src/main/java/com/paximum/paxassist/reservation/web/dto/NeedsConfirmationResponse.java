package com.paximum.paxassist.reservation.web.dto;

import java.util.List;

/**
 * Returned by POST /api/v1/reservations when setReservationInfo reported a warning that needs a second,
 * explicit user confirmation (e.g. DuplicateReservationFound). The client re-POSTs with
 * {@code confirmationToken} to proceed to commit.
 */
public record NeedsConfirmationResponse(
        String confirmationToken,
        List<String> warnings) {
}
