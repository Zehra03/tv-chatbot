package com.paximum.paxassist.config;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.cache.CacheManager;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.test.util.ReflectionTestUtils;

import com.paximum.paxassist.flight.domain.FlightProduct;
import com.paximum.paxassist.flight.service.FlightSearchOutcome;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RedisCacheConfigTest {

    @Test
    void cacheManager_ShouldCreateRedisCacheManagerWithConfiguredTtl() {
        RedisCacheConfig redisCacheConfig = new RedisCacheConfig();
        
        ReflectionTestUtils.setField(redisCacheConfig, "defaultTtlMinutes", 10);
        ReflectionTestUtils.setField(redisCacheConfig, "hotelTtlMinutes", 5);
        ReflectionTestUtils.setField(redisCacheConfig, "flightTtlMinutes", 5);

        RedisConnectionFactory connectionFactory = Mockito.mock(RedisConnectionFactory.class);
        
        CacheManager cacheManager = redisCacheConfig.cacheManager(connectionFactory);
        
        assertThat(cacheManager).isInstanceOf(RedisCacheManager.class);
        
        RedisCacheManager redisCacheManager = (RedisCacheManager) cacheManager;
        redisCacheManager.afterPropertiesSet();
        
        Map<String, RedisCacheConfiguration> cacheConfigs = redisCacheManager.getCacheConfigurations();
        
        assertThat(cacheConfigs).containsKey("hotelSearch");
        assertThat(cacheConfigs.get("hotelSearch").getTtl()).isEqualTo(Duration.ofMinutes(5));

        assertThat(cacheConfigs).containsKey("flightSearch");
        assertThat(cacheConfigs.get("flightSearch").getTtl()).isEqualTo(Duration.ofMinutes(5));
    }

    /**
     * Guards the cache-serialization fix: a FlightSearchOutcome holding a FlightProduct with
     * {@link Instant} fields must survive a Redis serialize/deserialize round-trip. Without the
     * JavaTimeModule the write threw "Java 8 date/time type not supported" (search 500'd after
     * fetching results); without {@code @Jacksonized} the read could not reconstruct the immutable
     * FlightProduct.
     */
    @Test
    void jsonRedisSerializer_roundTripsFlightOutcomeWithInstantFields() {
        GenericJackson2JsonRedisSerializer serializer = RedisCacheConfig.jsonRedisSerializer();

        FlightProduct flight = FlightProduct.builder()
                .id("TK7516")
                .airline("TK")
                .flightNumber("7516")
                .origin("SAW")
                .destination("AYT")
                .departTime(Instant.parse("2026-11-15T09:35:00Z"))
                .arriveTime(Instant.parse("2026-11-15T10:50:00Z"))
                .stops(0)
                .baggage("1x15kg")
                .price(new BigDecimal("21.27"))
                .currency("USD")
                .build();
        FlightSearchOutcome outcome = FlightSearchOutcome.complete(List.of(flight));

        Object restored = serializer.deserialize(serializer.serialize(outcome));

        assertThat(restored).isInstanceOf(FlightSearchOutcome.class);
        FlightSearchOutcome back = (FlightSearchOutcome) restored;
        assertThat(back.complete()).isTrue();
        assertThat(back.results()).hasSize(1);

        FlightProduct r = back.results().get(0);
        assertThat(r.getId()).isEqualTo("TK7516");
        assertThat(r.getOrigin()).isEqualTo("SAW");
        assertThat(r.getDestination()).isEqualTo("AYT");
        assertThat(r.getDepartTime()).isEqualTo(Instant.parse("2026-11-15T09:35:00Z"));
        assertThat(r.getArriveTime()).isEqualTo(Instant.parse("2026-11-15T10:50:00Z"));
        assertThat(r.getPrice()).isEqualByComparingTo(new BigDecimal("21.27"));
        assertThat(r.getCurrency()).isEqualTo("USD");
    }
}
