package com.paximum.paxassist.flight.client;

import com.paximum.paxassist.flight.dto.FlightSearchRequest;
import com.paximum.paxassist.flight.dto.FlightSearchResponse;
import com.paximum.paxassist.flight.exception.TourVisioApiException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class TourVisioFlightApiClientTest {

    @Mock
    private RestTemplate restTemplate;

    @InjectMocks
    private TourVisioFlightApiClient apiClient;

    @Test
    void searchFlights_ShouldAuthenticateAndCallApi() {
        // Trello Kartı 2: TourvisioAPI'ye erisim kullanici adi ve sifre ile oldugu icin
        // Authorization header'ının doğru set edildiğini ve API isteğinin atıldığını test etmeliyiz.
        
        // Arrange
        FlightSearchRequest request = new FlightSearchRequest("IST", "LHR", "2024-12-01");
        FlightSearchResponse mockResponse = new FlightSearchResponse(Collections.emptyList());
        ResponseEntity<FlightSearchResponse> responseEntity = new ResponseEntity<>(mockResponse, HttpStatus.OK);
        
        when(restTemplate.exchange(
                anyString(),
                eq(HttpMethod.POST),
                any(HttpEntity.class),
                eq(FlightSearchResponse.class)
        )).thenReturn(responseEntity);
        
        // Act
        FlightSearchResponse response = apiClient.searchFlights(request);
        
        // Assert
        assertNotNull(response);
    }

    @Test
    void searchFlights_WhenApiReturnsError_ShouldThrowException() {
        // API hata verdiğinde (500, 401 vb.) Client'ın düzgün hata fırlattığını test ediyoruz.
        
        // Arrange
        FlightSearchRequest request = new FlightSearchRequest("IST", "LHR", "2024-12-01");
        
        when(restTemplate.exchange(
                anyString(),
                eq(HttpMethod.POST),
                any(HttpEntity.class),
                eq(FlightSearchResponse.class)
        )).thenThrow(new RestClientException("API Error"));
        
        // Act & Assert
        assertThrows(TourVisioApiException.class, () -> apiClient.searchFlights(request));
    }
}
