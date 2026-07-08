package com.paximum.paxassist.config;

import com.paximum.paxassist.chat.dto.ErrorResponse;
import com.paximum.paxassist.chat.exception.AiClientException;
import com.paximum.paxassist.guard.GuardBlockedException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * Safety net for any caller that does not catch a guard block itself (the orchestrator's
     * primary path handles it inline). Returns only the safe standard message — the detailed
     * block reason is never exposed to the client.
     */
    @ExceptionHandler(GuardBlockedException.class)
    public ResponseEntity<ErrorResponse> handleGuardBlocked(GuardBlockedException e) {
        return ResponseEntity.badRequest()
                .body(new ErrorResponse("GUARD_BLOCKED", e.getMessage()));
    }

    @ExceptionHandler(AiClientException.class)
    public ResponseEntity<ErrorResponse> handleAiClientException(AiClientException e) {
        HttpStatus status = switch (e.getCode()) {
            case RATE_LIMITED -> HttpStatus.TOO_MANY_REQUESTS;
            case TIMEOUT, UNAVAILABLE -> HttpStatus.SERVICE_UNAVAILABLE;
            case UNKNOWN -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
        return ResponseEntity.status(status)
                .body(new ErrorResponse(e.getCode().name(), e.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException e) {
        var errors = new java.util.HashMap<String, String>();
        e.getBindingResult().getFieldErrors().forEach(fe -> 
            errors.put(fe.getField(), fe.getDefaultMessage())
        );
        
        return ResponseEntity.badRequest()
                .body(new ErrorResponse("VALIDATION_ERROR", "Geçersiz istek formu.", errors));
    }
}
