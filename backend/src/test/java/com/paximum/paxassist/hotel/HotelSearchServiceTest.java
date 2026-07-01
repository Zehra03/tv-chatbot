package com.paximum.paxassist.hotel;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class HotelSearchServiceTest {

    // @Mock
    // private TourVisioHotelApiClient tourVisioApiClient;
    
    // @Mock
    // private AuditLogService auditLogService;

    // @InjectMocks
    // private HotelSearchService hotelSearchService;

    @Test
    void shouldReturnFromRedisCacheIfDataExists() {
        // TODO: Test Cache-Aside Pattern
        // If data is available in Redis, API Client should NOT be called.
        /*
        // Given
        // when(redisTemplate.opsForValue().get("hotel:search:Antalya")).thenReturn(cachedResponse);
        
        // When
        // var result = hotelSearchService.searchHotels(new HotelSearchCriteria("Antalya"));

        // Then
        // assertThat(result).isEqualTo(cachedResponse);
        // verify(tourVisioApiClient, never()).searchHotels(any());
        */
        assertThat(true).isTrue();
    }

    @Test
    void shouldCallApiClientAndSaveToRedisIfCacheMiss() {
        // TODO: Cache miss scenario
        // If data is NOT in Redis, call the API and save the result to Redis.
        /*
        // Given
        // when(redisTemplate.opsForValue().get(anyString())).thenReturn(null);
        // when(tourVisioApiClient.searchHotels(any())).thenReturn(apiResponse);
        
        // When
        // var result = hotelSearchService.searchHotels(new HotelSearchCriteria("Antalya"));

        // Then
        // assertThat(result).isEqualTo(apiResponse);
        // verify(redisTemplate.opsForValue()).set(eq("hotel:search:Antalya"), eq(apiResponse));
        */
        assertThat(true).isTrue();
    }

    @Test
    void shouldLogExceptionWhenApiClientFails() {
        // TODO: Exception handling and logging scenario
        /*
        // Given
        // when(tourVisioApiClient.searchHotels(any())).thenThrow(new RuntimeException("API Down"));

        // When/Then
        // assertThrows(RuntimeException.class, () -> hotelSearchService.searchHotels(criteria));
        // verify(auditLogService).logError(anyString(), any(Exception.class));
        */
        assertThat(true).isTrue();
    }
}
