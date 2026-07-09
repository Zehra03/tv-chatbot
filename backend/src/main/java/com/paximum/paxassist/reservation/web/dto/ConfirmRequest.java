package com.paximum.paxassist.reservation.web.dto;

import jakarta.validation.constraints.AssertTrue;

/**
 * Body of POST /api/v1/reservations. Normally carries {@code previewId} to confirm a preview.
 * If {@code confirmationToken} is present instead, it resumes a booking that paused on a
 * setReservationInfo warning (second confirm) — this keeps the second-confirm on the same endpoint
 * rather than adding a new one.
 */
public record ConfirmRequest(
        String previewId,
        String confirmationToken) {

    /** Exactly one reference is expected; reject a body that carries neither previewId nor confirmationToken. */
    @AssertTrue(message = "Either previewId or confirmationToken must be provided")
    public boolean isReferencePresent() {
        return (previewId != null && !previewId.isBlank())
                || (confirmationToken != null && !confirmationToken.isBlank());
    }
}
