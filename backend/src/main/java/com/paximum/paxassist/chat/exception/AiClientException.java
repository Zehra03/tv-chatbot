package com.paximum.paxassist.chat.exception;

public class AiClientException extends RuntimeException {

    public enum Code { UNAVAILABLE, TIMEOUT, RATE_LIMITED, UNKNOWN }

    private final Code code;

    public AiClientException(Code code, String message) {
        super(message);
        this.code = code;
    }

    public AiClientException(Code code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    public Code getCode() { return code; }
}
