package com.paximum.paxassist.hotel;

import com.paximum.paxassist.common.log.LogModuleClient;
import com.paximum.paxassist.hotel.dto.AutocompleteResponse;
import com.paximum.paxassist.hotel.dto.HotelSearchRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Drives {@code searchHotels} through a real caching proxy (in-memory, no Redis needed) so the
 * {@code @Cacheable} SpEL key is actually evaluated — a plain unit test calls the bean directly and
 * would never catch a broken key expression.
 *
 * <p>The regression under guard: two searches differing only in children or nationality used to
 * collide on one key, serving one guest the other's price.
 */
@ExtendWith(SpringExtension.class)
class HotelSearchCacheKeyIntegrationTest {

    @Configuration
    @EnableCaching
    static class CachingTestConfig {
        @Bean
        ConcurrentMapCacheManager cacheManager() {
            return new ConcurrentMapCacheManager("hotelSearch");
        }

        @Bean
        TourVisioHotelApiClient tourVisioHotelApiClient() {
            return mock(TourVisioHotelApiClient.class);
        }

        @Bean
        LogModuleClient logModuleClient() {
            return mock(LogModuleClient.class);
        }

        @Bean
        HotelSearchService hotelSearchService(TourVisioHotelApiClient client, LogModuleClient logs) {
            return new HotelSearchServiceImpl(client, logs);
        }
    }

    @Autowired
    private HotelSearchService searchService;
    @Autowired
    private TourVisioHotelApiClient apiClient;
    @Autowired
    private ConcurrentMapCacheManager cacheManager;

    private static HotelSearchRequest search(List<Integer> childAges, String nationality, String checkIn) {
        return new HotelSearchRequest("Antalya", checkIn, 7, 2, childAges, nationality, "TRY", "tr-TR");
    }

    @BeforeEach
    void setUp() {
        reset(apiClient);
        cacheManager.getCache("hotelSearch").clear();
        when(apiClient.getArrivalAutocomplete("Antalya")).thenReturn(new AutocompleteResponse(
                new AutocompleteResponse.Header(true),
                new AutocompleteResponse.Body(List.of(
                        new AutocompleteResponse.Item(1, new AutocompleteResponse.City("23494", "Antalya"), null)))));
        when(apiClient.priceSearch(any(HotelSearchRequest.class), eq("23494")))
                .thenReturn(Map.of("searchId", "12345"));
    }

    @Test
    void searchWithChildren_doesNotReuseTheChildlessCachedPrice() {
        searchService.searchHotels(search(List.of(), "TR", "2026-08-01"));
        searchService.searchHotels(search(List.of(8), "TR", "2026-08-01"));

        // A cache hit here would hand the family the solo-couple price.
        verify(apiClient, times(2)).priceSearch(any(HotelSearchRequest.class), eq("23494"));
    }

    @Test
    void searchFromAnotherNationality_doesNotReuseTheCachedPrice() {
        searchService.searchHotels(search(List.of(), "TR", "2026-08-01"));
        searchService.searchHotels(search(List.of(), "DE", "2026-08-01"));

        verify(apiClient, times(2)).priceSearch(any(HotelSearchRequest.class), eq("23494"));
    }

    @Test
    void identicalSearch_isServedFromCache() {
        searchService.searchHotels(search(List.of(8), "TR", "2026-08-01"));
        searchService.searchHotels(search(List.of(8), "TR", "2026-08-01"));

        // Same party, same price — TourVisio must be hit only once (the cache still earns its keep).
        verify(apiClient, times(1)).priceSearch(any(HotelSearchRequest.class), eq("23494"));
    }

    @Test
    void incompleteSearch_isNotCachedAndDoesNotBreakTheKeyExpression() {
        HotelSearchRequest incomplete = new HotelSearchRequest(
                null, null, null, null, List.of(), "TR", "TRY", "tr-TR");

        // Must return the missing-parameter prompt rather than blowing up while building the key.
        searchService.searchHotels(incomplete);
        searchService.searchHotels(incomplete);

        verify(apiClient, org.mockito.Mockito.never()).priceSearch(any(), any());
    }
}
