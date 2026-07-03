package com.paximum.paxassist.ratelimiter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RateLimitResponseWriterTest {

    private final RateLimitResponseWriter writer = new RateLimitResponseWriter(new ObjectMapper());

    @Test
    void writeRejection_setsStatusHeaderAndBody() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        writer.writeRejection(response, 30L);

        assertThat(response.getStatus()).isEqualTo(429);
        assertThat(response.getHeader("Retry-After")).isEqualTo("30");
        assertThat(response.getContentType()).contains("application/json");
        assertThat(response.getCharacterEncoding()).isEqualTo("UTF-8");

        String body = new String(response.getContentAsByteArray(), StandardCharsets.UTF_8);
        assertThat(body).isEqualTo(
                "{\"message\":\"İstek limitinize ulaştınız.\",\"retryAfterSeconds\":30}");
    }

    @Test
    void writeRejection_bodyIsRealUtf8_forTurkishCharacters() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        writer.writeRejection(response, 5L);

        byte[] raw = response.getContentAsByteArray();
        // İ (U+0130) encodes to 0xC4 0xB0 and ı (U+0131) to 0xC4 0xB1 in UTF-8 — assert the
        // exact byte sequences appear, proving the body is UTF-8 rather than ASCII/escaped.
        assertThat(raw).containsSequence((byte) 0xC4, (byte) 0xB0); // İ
        assertThat(raw).containsSequence((byte) 0xC4, (byte) 0xB1); // ı
        assertThat(raw).containsSequence("İstek".getBytes(StandardCharsets.UTF_8));
        assertThat(raw).containsSequence("ulaştınız".getBytes(StandardCharsets.UTF_8));

        // Decoding as UTF-8 recovers the characters; decoding as ISO-8859-1 does not.
        assertThat(new String(raw, StandardCharsets.UTF_8)).contains("İstek limitinize ulaştınız.");
        assertThat(new String(raw, StandardCharsets.ISO_8859_1)).doesNotContain("İstek limitinize ulaştınız.");
    }

    @Test
    void writeRejection_serializationFailure_keeps429WithEmptyBody() throws Exception {
        ObjectMapper failing = mock(ObjectMapper.class);
        when(failing.writeValueAsBytes(any())).thenThrow(new JsonProcessingException("boom") {
        });
        RateLimitResponseWriter failingWriter = new RateLimitResponseWriter(failing);
        MockHttpServletResponse response = new MockHttpServletResponse();

        failingWriter.writeRejection(response, 10L);

        assertThat(response.getStatus()).isEqualTo(429);
        assertThat(response.getHeader("Retry-After")).isEqualTo("10");
        assertThat(response.getContentAsByteArray()).isEmpty();
    }
}
