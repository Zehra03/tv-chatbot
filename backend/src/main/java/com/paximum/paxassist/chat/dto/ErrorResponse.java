package com.paximum.paxassist.chat.dto;

import java.time.Instant;

public record ErrorResponse(String error, String message, Object details, Instant timestamp) {
    public ErrorResponse(String error, String message) {
        this(error, message, null, Instant.now());
    }
    
    public ErrorResponse(String error, String message, Object details) {
        this(error, message, details, Instant.now());
    }
}
