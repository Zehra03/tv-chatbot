package com.paximum.paxassist.flight.controller;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

import com.paximum.paxassist.flight.dto.FlightErrorResponse;
import com.paximum.paxassist.flight.infrastructure.client.TourVisioSearchException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FlightExceptionHandlerTest {

    private final FlightExceptionHandler handler = new FlightExceptionHandler();

    @Test
    void handleValidation_returnsBadRequestWithDetails() {
        MethodArgumentNotValidException ex = mock(MethodArgumentNotValidException.class);
        BindingResult bindingResult = mock(BindingResult.class);
        
        FieldError fieldError = new FieldError("flightSearchRequestDto", "currency", "size must be between 3 and 3");
        when(bindingResult.getFieldErrors()).thenReturn(List.of(fieldError));
        when(ex.getBindingResult()).thenReturn(bindingResult);

        ResponseEntity<FlightErrorResponse> response = handler.handleValidation(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().message()).isEqualTo("Invalid flight search request");
        assertThat(response.getBody().details()).containsExactly("currency: size must be between 3 and 3");
    }

    @Test
    void handleIllegalArgument_returnsBadRequest() {
        IllegalArgumentException ex = new IllegalArgumentException("Invalid argument");

        ResponseEntity<FlightErrorResponse> response = handler.handleIllegalArgument(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().message()).isEqualTo("Invalid argument");
        assertThat(response.getBody().details()).isEmpty();
    }

    @Test
    void handleTourVisioFailure_returnsBadGateway() {
        TourVisioSearchException ex = new TourVisioSearchException("API failed");

        ResponseEntity<FlightErrorResponse> response = handler.handleTourVisioFailure(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().message()).isEqualTo("Flight search is temporarily unavailable");
        assertThat(response.getBody().details()).isEmpty();
    }
}
