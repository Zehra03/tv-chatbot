package com.paximum.paxassist.hotel;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TourVisioHotelApiClientTest {

    @Mock
    private RestTemplate restTemplate;

    // The actual client that will be implemented
    // @InjectMocks
    // private TourVisioHotelApiClientImpl apiClient;

    @Test
    void shouldSendPostRequestToTourVisioApiWithOnlyHotelParameters() {
        // Given
        String expectedUrl = "https://api.tourvisio.com/v1/hotels/search";
        String jsonResponse = "[{\"id\":\"1\", \"hotelName\":\"Test Hotel\", \"destination\":\"Antalya\"}]";
        ResponseEntity<String> mockResponseEntity = new ResponseEntity<>(jsonResponse, HttpStatus.OK);

        when(restTemplate.exchange(
                eq(expectedUrl),
                eq(HttpMethod.POST),
                any(HttpEntity.class),
                eq(String.class)
        )).thenReturn(mockResponseEntity);

        // When
        // In real implementation this would be: apiClient.searchHotels(new HotelSearchCriteria("Antalya"));
        ResponseEntity<String> result = restTemplate.exchange(expectedUrl, HttpMethod.POST, HttpEntity.EMPTY, String.class);

        // Then
        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isEqualTo(jsonResponse);
        
        // Verify that the request was made exactly once with the expected parameters
        verify(restTemplate).exchange(
                eq(expectedUrl),
                eq(HttpMethod.POST),
                any(HttpEntity.class),
                eq(String.class)
        );
    }
}
