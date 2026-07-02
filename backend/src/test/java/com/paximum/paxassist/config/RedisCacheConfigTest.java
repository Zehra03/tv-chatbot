package com.paximum.paxassist.config;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.cache.CacheManager;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
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
}
