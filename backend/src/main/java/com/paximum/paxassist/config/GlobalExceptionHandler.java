package com.paximum.paxassist.config;

import java.util.Map;
import java.util.UUID;

import com.paximum.paxassist.chat.dto.ErrorResponse;
import com.paximum.paxassist.chat.exception.AiClientException;
import com.paximum.paxassist.guard.GuardBlockedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

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

    /**
     * A controller that rejects a request by throwing {@link ResponseStatusException} has already
     * decided the status (chat and reservation both do this for a missing/oversized
     * {@code X-Guest-Id}). Without this handler it falls through to the {@code Exception} catch-all
     * below and a deliberate 401/400 is reported to the client as a 500 "unexpected error" — the SPA
     * then shows a server-fault message instead of asking the user to sign in.
     *
     * <p>Only the status and the controller's own reason are echoed; there is no cause detail to leak
     * because these are hand-thrown, not wrapped.
     */
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ErrorResponse> handleResponseStatus(ResponseStatusException e) {
        HttpStatus status = HttpStatus.valueOf(e.getStatusCode().value());
        return ResponseEntity.status(status)
                .body(new ErrorResponse(status.name(), e.getReason() == null ? status.getReasonPhrase() : e.getReason()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException e) {
        var errors = new java.util.HashMap<String, String>();
        boolean outOfRange = false;
        
        for (var fe : e.getBindingResult().getFieldErrors()) {
            errors.put(fe.getField(), fe.getDefaultMessage());
            if ("OUT_OF_CALENDAR_RANGE".equals(fe.getDefaultMessage())) {
                outOfRange = true;
            }
        }

        if (outOfRange) {
            return ResponseEntity.badRequest()
                    .body(new ErrorResponse("OUT_OF_CALENDAR_RANGE", "Arama tarihi takvim sınırlarını aşıyor.", errors));
        }

        return ResponseEntity.badRequest()
                .body(new ErrorResponse("VALIDATION_ERROR", "Geçersiz istek formu.", errors));
    }

    /**
     * Catch-all so an unhandled failure (NPE, a dropped DB connection, a TourVisio parse error) still
     * answers on the {@link ErrorResponse} contract instead of falling through to Spring's default
     * {@code /error} page — which renders whatever {@code server.error.include-*} happens to allow and
     * can leak an exception message or stack trace to the client.
     *
     * <p>The body is fixed and says nothing about the cause. The detail goes to the log only, under a
     * generated {@code errorId} that is echoed to the caller — enough to find the log line for a
     * reported error, useless to anyone probing the API.
     *
     * <p>Declared last and typed {@code Exception}, so it only runs when no more specific handler
     * above (or in a module's own {@code @RestControllerAdvice}) matched.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception e) {
        String errorId = UUID.randomUUID().toString();
        log.error("Unhandled exception errorId={}", errorId, e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorResponse("INTERNAL_ERROR",
                        "Beklenmeyen bir hata oluştu. Lütfen daha sonra tekrar deneyin.",
                        Map.of("errorId", errorId)));
    }
}
