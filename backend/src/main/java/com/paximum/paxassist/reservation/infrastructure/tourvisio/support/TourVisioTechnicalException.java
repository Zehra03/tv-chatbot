package com.paximum.paxassist.reservation.infrastructure.tourvisio.support;

/**
 * Raised for a <em>definite</em> technical failure where the request certainly did NOT
 * take effect on TourVisio's side — e.g. a 4xx (bad request / rejected), or auth still
 * failing after a forced re-login. Not ambiguous, so never surfaces as {@code UnknownOutcome}.
 */
public class TourVisioTechnicalException extends RuntimeException {

    public TourVisioTechnicalException(String message, Throwable cause) {
        super(message, cause);
    }
}
