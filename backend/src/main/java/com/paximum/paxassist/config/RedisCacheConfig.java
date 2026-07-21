package com.paximum.paxassist.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.time.Duration;

@Configuration
@EnableCaching
public class RedisCacheConfig {

    @Value("${app.cache.default-ttl-minutes:10}")
    private long defaultTtlMinutes;

    @Value("${app.cache.hotel-ttl-minutes:5}")
    private long hotelTtlMinutes;

    @Value("${app.cache.flight-ttl-minutes:5}")
    private long flightTtlMinutes;

    @Bean
    public CacheManager cacheManager(RedisConnectionFactory connectionFactory) {
        // Configure key and value serializers to use String and JSON format respectively
        RedisCacheConfiguration defaultConfig = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofMinutes(defaultTtlMinutes))
                .disableCachingNullValues()
                .serializeKeysWith(RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(jsonRedisSerializer()));

        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(defaultConfig)
                .withCacheConfiguration("hotelSearch", defaultConfig.entryTtl(Duration.ofMinutes(hotelTtlMinutes)))
                .withCacheConfiguration("flightSearch", defaultConfig.entryTtl(Duration.ofMinutes(flightTtlMinutes)))
                .build();
    }

    /**
     * JSON value serializer with Java 8 date/time support. The default
     * {@link GenericJackson2JsonRedisSerializer} builds a plain ObjectMapper without the
     * {@link JavaTimeModule}, so caching a FlightProduct (Instant departTime/arriveTime) threw
     * "Java 8 date/time type not supported" on the cache write and the flight search 500'd after
     * already fetching results. Default typing is preserved (matching the no-arg default) so cached
     * values still deserialize back to their concrete types; dates are written as ISO strings.
     */
    // Package-private for RedisCacheConfigTest (exercises the FlightProduct/Instant round-trip).
    static GenericJackson2JsonRedisSerializer jsonRedisSerializer() {
        GenericJackson2JsonRedisSerializer serializer = new GenericJackson2JsonRedisSerializer();
        // Keep the serializer's own @class default typing (needed to deserialize cached values back
        // to their concrete types, incl. the final FlightSearchOutcome root); only teach its
        // ObjectMapper about Java 8 dates so FlightProduct's Instant fields serialize as ISO strings
        // instead of throwing "Java 8 date/time type not supported" on the cache write.
        serializer.configure(mapper -> {
            mapper.registerModule(new JavaTimeModule());
            mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
            // A cached value is disposable: if its JSON carries a property the current class no longer
            // maps (a renamed/removed field, or a derived getter that leaked in), treat it as a cache
            // miss and re-fetch — never let a stale entry 500 the whole search. This is what turned a
            // BaggageAllowance "unknown" field left in Redis into an INTERNAL_ERROR on every cache hit.
            mapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        });
        return serializer;
    }
}
