package com.paximum.paxassist.flight.controller;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.paximum.paxassist.flight.dto.FlightErrorResponse;
import com.paximum.paxassist.flight.infrastructure.client.TourVisioSearchException;

@RestControllerAdvice(basePackages = "com.paximum.paxassist.flight.controller")
public class FlightExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(FlightExceptionHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<FlightErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
        List<String> details = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .toList();
        return ResponseEntity.badRequest().body(new FlightErrorResponse("Invalid flight search request", details));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<FlightErrorResponse> handleIllegalArgument(IllegalArgumentException ex) {
        return ResponseEntity.badRequest().body(new FlightErrorResponse(ex.getMessage(), List.of()));
    }

    @ExceptionHandler(TourVisioSearchException.class)
    public ResponseEntity<FlightErrorResponse> handleTourVisioFailure(TourVisioSearchException ex) {
        log.error("Flight search failed: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(new FlightErrorResponse("Flight search is temporarily unavailable", List.of()));
    }
}
