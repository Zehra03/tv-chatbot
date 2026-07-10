package com.paximum.paxassist.orchestrator.date;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

import com.paximum.paxassist.ai.SlotCriteria;

import static org.assertj.core.api.Assertions.assertThat;

class TravelDateGuardTest {

    // Fixed "today" = 2026-07-08.
    private final TravelDateGuard guard =
            new TravelDateGuard(Clock.fixed(Instant.parse("2026-07-08T00:00:00Z"), ZoneOffset.UTC));
    private final ObjectMapper objectMapper = new ObjectMapper();

    private SlotCriteria slots(Map<String, Object> fields) {
        return objectMapper.convertValue(fields, SlotCriteria.class);
    }

    @Test
    void nullCriteria_returnsEmpty() {
        assertThat(guard.checkPastDate(null)).isEmpty();
    }

    @Test
    void futureCheckIn_returnsEmpty() {
        assertThat(guard.checkPastDate(slots(Map.of("checkIn", "2026-08-01")))).isEmpty();
    }

    @Test
    void today_isNotPast() {
        assertThat(guard.checkPastDate(slots(Map.of("checkIn", "2026-07-08")))).isEmpty();
    }

    @Test
    void pastCheckIn_returnsFriendlyMessageWithTheDate() {
        Optional<String> msg = guard.checkPastDate(slots(Map.of("checkIn", "2026-06-25")));
        assertThat(msg).isPresent();
        assertThat(msg.get()).contains("2026-06-25").contains("geçmiş");
    }

    @Test
    void pastDepartureDate_returnsMessage() {
        assertThat(guard.checkPastDate(slots(Map.of("departureDate", "2026-07-07")))).isPresent();
    }

    @Test
    void futureCheckInButPastCheckOut_returnsMessage() {
        Optional<String> msg = guard.checkPastDate(
                slots(Map.of("checkIn", "2026-08-01", "checkOut", "2026-06-01")));
        assertThat(msg).isPresent();
        assertThat(msg.get()).contains("2026-06-01");
    }

    @Test
    void unparsableDate_isIgnored() {
        assertThat(guard.checkPastDate(slots(Map.of("checkIn", "yarın")))).isEmpty();
    }
}
