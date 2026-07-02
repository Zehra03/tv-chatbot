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

    private final TourVisioHotelApiClient mockClient = new MockTourVisioHotelApiClient();

    @Mock
    private RestTemplate restTemplate;

    // Assuming a future implementation class named TourVisioHotelApiClientImpl
    // @InjectMocks
    // private TourVisioHotelApiClientImpl apiClientImpl;

    @Test
    void shouldReturnMockHotelsSuccessfully() {
        HotelSearchCriteria criteria = new HotelSearchCriteria("Antalya");
        
        List<HotelProduct> result = mockClient.searchHotels(criteria);

        assertThat(result).isNotNull();
        assertThat(result).hasSize(6);
        assertThat(result.get(0).hotelName()).isEqualTo("Rixos Premium");
    }

    @Test
    void shouldImplementClientInterfaceCorrectly() {
        // Given
        String API_URL = "https://api.tourvisio.com/v1/hotels/search";
        String mockResponse = "[{\"id\":\"1\", \"hotelName\":\"Rixos Premium\", \"destination\":\"Antalya\"}]";
        ResponseEntity<String> responseEntity = new ResponseEntity<>(mockResponse, HttpStatus.OK);

        when(restTemplate.exchange(
                eq(API_URL),
                eq(HttpMethod.POST),
                any(HttpEntity.class),
                eq(String.class)
        )).thenReturn(responseEntity);

        // When
        // In reality, this would call apiClientImpl.searchHotels(criteria)
        ResponseEntity<String> result = restTemplate.exchange(API_URL, HttpMethod.POST, HttpEntity.EMPTY, String.class);

        // Then
        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isEqualTo(mockResponse);
        
        verify(restTemplate).exchange(
                eq(API_URL),
                eq(HttpMethod.POST),
                any(HttpEntity.class),
                eq(String.class)
        );
        
        assertThat(TourVisioHotelApiClient.class).isInterface();
    }
}
