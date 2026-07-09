package com.paximum.paxassist.hotel;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.paximum.paxassist.hotel.dto.AutocompleteResponse;
import com.paximum.paxassist.hotel.dto.HotelSearchRequest;
import com.paximum.paxassist.hotel.dto.LoginRequest;
import com.paximum.paxassist.hotel.dto.LoginResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TourVisioHotelApiClientTest {

    @Mock
    private RestTemplate restTemplate;

    private TourVisioHotelApiClientImpl apiClient;

    @BeforeEach
    void setUp() {
        apiClient = new TourVisioHotelApiClientImpl(new ObjectMapper());
        ReflectionTestUtils.setField(apiClient, "restTemplate", restTemplate);
        ReflectionTestUtils.setField(apiClient, "baseUrl", "https://api.tourvisio.com");
        ReflectionTestUtils.setField(apiClient, "agency", "testAgency");
        ReflectionTestUtils.setField(apiClient, "username", "testUser");
        ReflectionTestUtils.setField(apiClient, "password", "testPass");
    }

    @Test
    void shouldAuthenticateSuccessfully() {
        // Given
        LoginResponse mockResponse = new LoginResponse(
                new LoginResponse.Header(true),
                new LoginResponse.Body("mock-token", "2099-12-31T23:59:59Z")
        );
        ResponseEntity<LoginResponse> responseEntity = new ResponseEntity<>(mockResponse, HttpStatus.OK);

        when(restTemplate.postForEntity(
                anyString(),
                any(LoginRequest.class),
                eq(LoginResponse.class)
        )).thenReturn(responseEntity);

        // When
        String token = apiClient.authenticate();

        // Then
        assertThat(token).isEqualTo("mock-token");
        verify(restTemplate).postForEntity(
                eq("https://api.tourvisio.com/api/authenticationservice/login"),
                any(LoginRequest.class),
                eq(LoginResponse.class)
        );
    }

    @Test
    void shouldGetArrivalAutocompleteSuccessfully() {
        // Given
        ReflectionTestUtils.setField(apiClient, "cachedToken", "mock-token");
        ReflectionTestUtils.setField(apiClient, "tokenExpiry", java.time.Instant.now().plusSeconds(3600));

        AutocompleteResponse mockResponse = new AutocompleteResponse(
                new AutocompleteResponse.Header(true),
                new AutocompleteResponse.Body(List.of(
                        new AutocompleteResponse.Item(1, new AutocompleteResponse.City("123", "Antalya"), null)
                ))
        );
        ResponseEntity<AutocompleteResponse> responseEntity = new ResponseEntity<>(mockResponse, HttpStatus.OK);

        when(restTemplate.postForEntity(
                anyString(),
                any(HttpEntity.class),
                eq(AutocompleteResponse.class)
        )).thenReturn(responseEntity);

        // When
        AutocompleteResponse result = apiClient.getArrivalAutocomplete("Antalya");

        // Then
        assertThat(result.body().items().get(0).city().name()).isEqualTo("Antalya");
        verify(restTemplate).postForEntity(
                eq("https://api.tourvisio.com/api/productservice/getarrivalautocomplete"),
                any(HttpEntity.class),
                eq(AutocompleteResponse.class)
        );
    }

    @Test
    void shouldPerformPriceSearchSuccessfully() {
        // Given
        ReflectionTestUtils.setField(apiClient, "cachedToken", "mock-token");
        ReflectionTestUtils.setField(apiClient, "tokenExpiry", java.time.Instant.now().plusSeconds(3600));

        HotelSearchRequest criteria = new HotelSearchRequest("Antalya", "2024-01-01", 5, 2, List.of(), "TR", "TRY", "tr-TR");
        Object mockResponse = java.util.Map.of("body", java.util.Map.of("hotels", java.util.List.of()));
        ResponseEntity<Object> responseEntity = new ResponseEntity<>(mockResponse, HttpStatus.OK);

        when(restTemplate.postForEntity(
                anyString(),
                any(HttpEntity.class),
                eq(Object.class)
        )).thenReturn(responseEntity);

        // When
        Object result = apiClient.priceSearch(criteria, "123");

        // Then
        assertThat(result).isNotNull();
        verify(restTemplate).postForEntity(
                eq("https://api.tourvisio.com/api/productservice/pricesearch"),
                any(HttpEntity.class),
                eq(Object.class)
        );
    }
}
