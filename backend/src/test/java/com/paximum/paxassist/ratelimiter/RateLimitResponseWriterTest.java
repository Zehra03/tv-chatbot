package com.paximum.paxassist.ratelimiter;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class RateLimitResponseWriterTest {

    private RateLimitResponseWriter writer;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        writer = new RateLimitResponseWriter(objectMapper);
    }

    @Test
    void shouldWriteRejectionResponse() throws IOException {
        HttpServletResponse response = Mockito.mock(HttpServletResponse.class);
        ServletOutputStream outputStream = Mockito.mock(ServletOutputStream.class);
        when(response.getOutputStream()).thenReturn(outputStream);

        writer.writeRejection(response, 120);

        verify(response).setStatus(429);
        verify(response).setHeader("Retry-After", "120");
        verify(response).setContentType("application/json");
        verify(response).setCharacterEncoding("UTF-8");

        ArgumentCaptor<byte[]> captor = ArgumentCaptor.forClass(byte[].class);
        verify(outputStream).write(captor.capture());

        String writtenBody = new String(captor.getValue());
        assertTrue(writtenBody.contains("\"message\":\"İstek limitinize ulaştınız.\""));
        assertTrue(writtenBody.contains("\"retryAfterSeconds\":120"));
    }
    
    @Test
    void shouldNotThrowExceptionWhenSerializationFails() throws IOException {
        ObjectMapper failingMapper = mock(ObjectMapper.class);
        when(failingMapper.writeValueAsBytes(any())).thenThrow(mock(com.fasterxml.jackson.core.JsonProcessingException.class));
        
        RateLimitResponseWriter failingWriter = new RateLimitResponseWriter(failingMapper);
        
        HttpServletResponse response = Mockito.mock(HttpServletResponse.class);
        
        failingWriter.writeRejection(response, 60);
        
        verify(response).setStatus(429);
        // It shouldn't attempt to write to output stream
        verify(response, never()).getOutputStream();
    }
}
