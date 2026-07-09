package com.paximum.paxassist.ratelimiter;

import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisConnectionException;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.codec.RedisCodec;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RateLimitRedisConfigTest {

    private final RateLimitRedisConfig config = new RateLimitRedisConfig();

    @Test
    void shouldThrowExceptionWhenFactoryIsNotLettuce() {
        RedisConnectionFactory factory = mock(RedisConnectionFactory.class);
        
        IllegalStateException ex = assertThrows(IllegalStateException.class, 
                () -> config.rateLimitRedisConnection(factory));
        
        assertTrue(ex.getMessage().contains("requires a LettuceConnectionFactory"));
    }

    @Test
    void shouldThrowExceptionWhenNativeClientIsNotRedisClient() {
        LettuceConnectionFactory factory = mock(LettuceConnectionFactory.class);
        io.lettuce.core.AbstractRedisClient clusterClient = mock(io.lettuce.core.cluster.RedisClusterClient.class);
        when(factory.getNativeClient()).thenReturn(clusterClient);
        
        IllegalStateException ex = assertThrows(IllegalStateException.class, 
                () -> config.rateLimitRedisConnection(factory));
        
        assertTrue(ex.getMessage().contains("Expected a standalone Lettuce RedisClient"));
    }

    @Test
    void shouldConnectSuccessfully() {
        LettuceConnectionFactory factory = mock(LettuceConnectionFactory.class);
        RedisClient redisClient = mock(RedisClient.class);
        @SuppressWarnings("unchecked")
        StatefulRedisConnection<String, byte[]> connection = mock(StatefulRedisConnection.class);
        
        when(factory.getNativeClient()).thenReturn(redisClient);
        when(redisClient.connect(any(RedisCodec.class))).thenReturn(connection);
        
        StatefulRedisConnection<String, byte[]> result = config.rateLimitRedisConnection(factory);
        
        assertNotNull(result);
        assertEquals(connection, result);
    }

    @Test
    void shouldThrowRedisConnectionExceptionOnFailure() {
        LettuceConnectionFactory factory = mock(LettuceConnectionFactory.class);
        RedisClient redisClient = mock(RedisClient.class);
        
        when(factory.getNativeClient()).thenReturn(redisClient);
        when(redisClient.connect(any(RedisCodec.class)))
                .thenThrow(io.lettuce.core.RedisConnectionException.create("localhost", new RuntimeException("Test error")));
        
        assertThrows(RedisConnectionException.class, 
                () -> config.rateLimitRedisConnection(factory));
    }

}
