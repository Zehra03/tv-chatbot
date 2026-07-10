package com.paximum.paxassist.auth.exception;

/**
 * Thrown when a presented refresh token cannot be honoured: unknown, already rotated out, revoked
 * (logout), or expired. Surfaced as 401 {@code INVALID_REFRESH_TOKEN} so the client falls back to a
 * full re-login. The message is deliberately generic — it never reveals which of those it was.
 */
public class InvalidRefreshTokenException extends RuntimeException {

    public InvalidRefreshTokenException() {
        super("Refresh token is invalid or expired");
    }
}
