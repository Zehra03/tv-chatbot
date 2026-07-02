package com.paximum.paxassist.hotel;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HotelSearchServiceTest {

    @Mock
    private TourVisioHotelApiClient tourVisioApiClient;

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private ValueOperations<String, Object> valueOperations;

    @InjectMocks
    private HotelSearchService hotelSearchService;

    @Test
    void shouldReturnFromRedisCacheWithoutCallingApiIfDataExists() {
        // Given
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        List<HotelProduct> cachedResponse = List.of(new HotelProduct("1", "Rixos Premium", "Antalya"));
        when(valueOperations.get("hotel:search:Antalya")).thenReturn(cachedResponse);
        
        // When
        List<HotelProduct> result = hotelSearchService.searchHotels(new HotelSearchCriteria("Antalya"));

        // Then
        assertThat(result).isEqualTo(cachedResponse);
        // Ensure API is NEVER called when cache hits
        verify(tourVisioApiClient, never()).searchHotels(any());
    }

    @Test
    void shouldCallTourVisioApiAndSaveToRedisIfCacheMisses() {
        // Given
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(anyString())).thenReturn(null);
        
        List<HotelProduct> apiResponse = List.of(new HotelProduct("2", "Titanic Mardan", "Antalya"));
        when(tourVisioApiClient.searchHotels(any())).thenReturn(apiResponse);
        
        // When
        List<HotelProduct> result = hotelSearchService.searchHotels(new HotelSearchCriteria("Antalya"));

        // Then
        assertThat(result).isEqualTo(apiResponse);
        // Ensure result is saved to cache
        verify(valueOperations).set(eq("hotel:search:Antalya"), eq(apiResponse));
    }
}
