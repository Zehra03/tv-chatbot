package com.paximum.paxassist.config;

import com.paximum.paxassist.chat.dto.ErrorResponse;
import com.paximum.paxassist.chat.exception.AiClientException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

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
