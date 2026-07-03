package com.paximum.paxassist.ratelimiter;

import io.github.bucket4j.ConsumptionProbe;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Writes the HTTP response when a request is rejected by rate limiting.
 *
 * <p>Placeholder implementation — this card only wires it into the filter. The full
 * response (429 body, Retry-After from the probe, X-RateLimit-* headers) is built in
 * Card 4.
 */
@Component
public class RateLimitResponseWriter {

    // TODO: Card 4 — full 429 body (e.g. application/problem+json), Retry-After derived
    //       from probe.getNanosToWaitForRefill(), and X-RateLimit-* headers.
    public void writeLimitExceeded(HttpServletRequest request,
                                   HttpServletResponse response,
                                   ConsumptionProbe probe) throws IOException {
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
    }
}
