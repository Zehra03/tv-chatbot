package com.paximum.paxassist.flight.service;

import com.paximum.paxassist.flight.domain.FlightSearchCriteria;
import com.paximum.paxassist.flight.domain.PassengerCount;
import com.paximum.paxassist.flight.domain.TripType;
import com.paximum.paxassist.flight.infrastructure.client.TourVisioFlightClient;
import com.paximum.paxassist.flight.infrastructure.dto.response.TourVisioPriceSearchResponse;
import com.paximum.paxassist.flight.infrastructure.dto.response.TourVisioResponseBody;
import com.paximum.paxassist.flight.infrastructure.dto.response.TourVisioResponseHeader;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.LocalDate;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Testcontainers
class FlightCacheIntegrationTest {

    @Container
    public static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void redisProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", redis::getFirstMappedPort);
    }

    @Autowired
    private FlightSearchService flightSearchService;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @MockBean
    private TourVisioFlightClient tourVisioFlightClient;

    @MockBean
    private com.paximum.paxassist.flight.infrastructure.client.TourVisioTokenProvider tourVisioTokenProvider;

    private FlightSearchCriteria sampleCriteria;

    @BeforeEach
    void setUp() {
        redisTemplate.getConnectionFactory().getConnection().serverCommands().flushAll();

        sampleCriteria = FlightSearchCriteria.builder()
                .origin("IST")
                .destination("AYT")
                .departDate(LocalDate.of(2027, 1, 1))
                .tripType(TripType.ONE_WAY)
                .passengers(PassengerCount.builder().adults(1).children(0).infants(0).build())
                .currency("TRY")
                .build();

        TourVisioResponseHeader header = new TourVisioResponseHeader(true);
        TourVisioResponseBody body = new TourVisioResponseBody(null); // Assuming empty list mapped gracefully
        TourVisioPriceSearchResponse mockResponse = new TourVisioPriceSearchResponse(header, body);

        when(tourVisioFlightClient.priceSearch(any())).thenReturn(mockResponse);
    }

    @Test
    void testCacheMissAndHit() {
        // --- Cache Miss Senaryosu ---
        FlightSearchOutcome outcome1 = flightSearchService.search(sampleCriteria);
        assertThat(outcome1).isNotNull();
        assertThat(outcome1.isSuccess()).isTrue();

        // Dış API tam 1 kez çağrılmış olmalı
        verify(tourVisioFlightClient, times(1)).priceSearch(any());

        // --- Cache Hit Senaryosu ---
        FlightSearchOutcome outcome2 = flightSearchService.search(sampleCriteria);
        assertThat(outcome2).isNotNull();
        assertThat(outcome2.isSuccess()).isTrue();

        // Dış API 2. kez çağrılmamalı
        verify(tourVisioFlightClient, times(1)).priceSearch(any());
    }

    @Test
    void testCacheTtlExpiration() throws InterruptedException {
        // İlk çağrı
        flightSearchService.search(sampleCriteria);
        verify(tourVisioFlightClient, times(1)).priceSearch(any());

        // Redis'teki tüm keylerin TTL değerini manuel olarak 1 milisaniyeye düşürelim
        var keys = redisTemplate.keys("*");
        assertThat(keys).isNotEmpty();
        for (String key : keys) {
            redisTemplate.expire(key, 1, TimeUnit.MILLISECONDS);
        }

        // Sürenin dolmasını bekleyelim
        Thread.sleep(50);

        // İkinci çağrı - Süre dolduğu için dış API'ye tekrar gitmeli
        flightSearchService.search(sampleCriteria);

        // Dış API çağrı sayısı 2'ye çıkmış olmalı
        verify(tourVisioFlightClient, times(2)).priceSearch(any());
    }
}
