package com.paximum.paxassist.hotel;

import com.paximum.paxassist.common.log.LogModuleClient;
import com.paximum.paxassist.hotel.dto.AutocompleteResponse;
import com.paximum.paxassist.hotel.dto.HotelSearchRequest;
import com.paximum.paxassist.hotel.dto.HotelSearchResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.cache.CacheManager;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Testcontainers
class HotelCacheIntegrationTest {

    @Container
    public static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void redisProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", redis::getFirstMappedPort);
    }

    @Autowired
    private HotelSearchService hotelSearchService;

    @Autowired
    private CacheManager cacheManager;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @MockBean
    private TourVisioHotelApiClient tourVisioHotelApiClient;

    @MockBean
    private LogModuleClient logModuleClient;

    private HotelSearchRequest sampleRequest;

    @BeforeEach
    void setUp() {
        // Clear redis data before each test
        redisTemplate.getConnectionFactory().getConnection().serverCommands().flushAll();

        sampleRequest = new HotelSearchRequest(
                "Antalya",
                "2027-01-01",
                5,
                2,
                null,
                "TR",
                "TRY",
                "tr-TR"
        );

        AutocompleteResponse.City city = new AutocompleteResponse.City("loc-1", "Antalya");
        AutocompleteResponse.Item item = new AutocompleteResponse.Item(1, city, null);
        AutocompleteResponse.Body body = new AutocompleteResponse.Body(List.of(item));
        AutocompleteResponse autocompleteResponse = new AutocompleteResponse(new AutocompleteResponse.Header(true), body);

        when(tourVisioHotelApiClient.getArrivalAutocomplete("Antalya")).thenReturn(autocompleteResponse);
        when(tourVisioHotelApiClient.priceSearch(any(), eq("loc-1"))).thenReturn("MOCK_SEARCH_RESULT");
    }

    @Test
    void testCacheMissAndHit() {
        // --- Cache Miss Senaryosu ---
        HotelSearchResponse response1 = hotelSearchService.searchHotels(sampleRequest);
        assertThat(response1).isNotNull();
        assertThat(response1.status()).isEqualTo("SUCCESS");
        
        // Dış API 1 kez çağrılmış olmalı
        verify(tourVisioHotelApiClient, times(1)).getArrivalAutocomplete("Antalya");
        verify(tourVisioHotelApiClient, times(1)).priceSearch(any(), eq("loc-1"));

        // --- Cache Hit Senaryosu ---
        HotelSearchResponse response2 = hotelSearchService.searchHotels(sampleRequest);
        assertThat(response2).isNotNull();
        assertThat(response2.status()).isEqualTo("SUCCESS");
        
        // Dış API çağrı sayısı artmamış olmalı (önbellekten döndü)
        verify(tourVisioHotelApiClient, times(1)).getArrivalAutocomplete("Antalya");
        verify(tourVisioHotelApiClient, times(1)).priceSearch(any(), eq("loc-1"));
    }

    @Test
    void testCacheTtlExpiration() throws InterruptedException {
        // İlk çağrı - Cache'e yazılır
        hotelSearchService.searchHotels(sampleRequest);
        verify(tourVisioHotelApiClient, times(1)).getArrivalAutocomplete("Antalya");
        verify(tourVisioHotelApiClient, times(1)).priceSearch(any(), eq("loc-1"));

        // Redis'teki tüm keylerin TTL değerini manuel olarak 1 milisaniyeye düşürelim
        var keys = redisTemplate.keys("*");
        assertThat(keys).isNotEmpty();
        for (String key : keys) {
            redisTemplate.expire(key, 1, TimeUnit.MILLISECONDS);
        }

        // Sürenin dolmasını bekleyelim
        Thread.sleep(50);

        // İkinci çağrı - Süre dolduğu için dış API'ye tekrar gitmeli
        hotelSearchService.searchHotels(sampleRequest);
        
        // Dış API çağrı sayısı 2'ye çıkmış olmalı
        verify(tourVisioHotelApiClient, times(2)).getArrivalAutocomplete("Antalya");
        verify(tourVisioHotelApiClient, times(2)).priceSearch(any(), eq("loc-1"));
    }
}
