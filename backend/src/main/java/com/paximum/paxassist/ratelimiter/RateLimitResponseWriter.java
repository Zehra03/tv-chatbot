package com.paximum.paxassist.ratelimiter;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Writes the 429 response body when a request is rejected by rate limiting.
 */
@Component
public class RateLimitResponseWriter {

    private static final Logger log = LoggerFactory.getLogger(RateLimitResponseWriter.class);

    private static final int STATUS_TOO_MANY_REQUESTS = 429;
    private static final String LIMIT_MESSAGE = "İstek limitinize ulaştınız.";

    private final ObjectMapper objectMapper;

    public RateLimitResponseWriter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public void writeRejection(HttpServletResponse response, long retryAfterSeconds) throws IOException {
        response.setStatus(STATUS_TOO_MANY_REQUESTS);
        response.setHeader("Retry-After", String.valueOf(retryAfterSeconds));
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        byte[] body;
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("message", LIMIT_MESSAGE);
            payload.put("retryAfterSeconds", retryAfterSeconds);
            body = objectMapper.writeValueAsBytes(payload);
        } catch (IOException e) {
            // Serialization failure must not turn into a 500 — keep the 429 with an empty body.
            log.error("Failed to serialize rate limit rejection body", e);
            return;
        }

        response.getOutputStream().write(body);
    }
}
