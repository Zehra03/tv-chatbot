package com.paximum.paxassist.hotel;

import com.paximum.paxassist.audit.AuditLogService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
    private AuditLogService auditLogService;

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private ValueOperations<String, Object> valueOperations;

    @InjectMocks
    private HotelSearchService hotelSearchService;

    @BeforeEach
    void setUp() {
        // lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @Test
    void shouldReturnFromRedisCacheIfDataExists() {
        // Given
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        List<HotelProduct> cachedResponse = List.of(new HotelProduct("1", "Rixos Premium", "Antalya"));
        when(valueOperations.get("hotel:search:Antalya")).thenReturn(cachedResponse);

        // When
        var result = hotelSearchService.searchHotels(new HotelSearchCriteria("Antalya"));

        // Then
        assertThat(result).isEqualTo(cachedResponse);
        verify(tourVisioApiClient, never()).searchHotels(any());
    }

    @Test
    void shouldCallApiClientAndSaveToRedisIfCacheMiss() {
        // Given
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(anyString())).thenReturn(null);

        List<HotelProduct> apiResponse = List.of(new HotelProduct("2", "Titanic Mardan", "Antalya"));
        when(tourVisioApiClient.searchHotels(any())).thenReturn(apiResponse);

        // When
        var result = hotelSearchService.searchHotels(new HotelSearchCriteria("Antalya"));

        // Then
        assertThat(result).isEqualTo(apiResponse);
        verify(valueOperations).set(eq("hotel:search:Antalya"), eq(apiResponse));
    }

    @Test
    void shouldLogExceptionWhenApiClientFails() {
        // Given
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(anyString())).thenReturn(null);
        when(tourVisioApiClient.searchHotels(any())).thenThrow(new RuntimeException("API Down"));

        HotelSearchCriteria criteria = new HotelSearchCriteria("Antalya");

        // When/Then
        assertThrows(RuntimeException.class, () -> hotelSearchService.searchHotels(criteria));
        verify(auditLogService).logError(anyString(), any(Exception.class));
    }
}
