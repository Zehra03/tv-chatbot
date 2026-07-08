package com.paximum.paxassist.validator;

/**
 * Validator-local equivalent of {@code chat.exception.AiClientException} — kept as a separate type
 * (rather than importing the {@code chat} package's version) so the Validator module has zero
 * compile-time dependency on the forbidden {@code chat}/{@code ai} packages.
 */
public class ValidatorException extends RuntimeException {

    public enum Code { UNAVAILABLE, TIMEOUT, RATE_LIMITED, UNKNOWN }

    private final Code code;

    public ValidatorException(Code code, String message) {
        super(message);
        this.code = code;
    }

    public ValidatorException(Code code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    public Code getCode() {
        return code;
    }
}
