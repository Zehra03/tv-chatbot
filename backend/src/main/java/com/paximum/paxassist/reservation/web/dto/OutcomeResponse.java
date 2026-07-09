package com.paximum.paxassist.reservation.web.dto;

/**
 * Minimal body for non-success outcomes so each endpoint returns something meaningful. This is plain
 * plumbing only — the standardized error contract / global exception handling is ticket 5.
 */
public record OutcomeResponse(
        String outcome,
        String message) {
}
