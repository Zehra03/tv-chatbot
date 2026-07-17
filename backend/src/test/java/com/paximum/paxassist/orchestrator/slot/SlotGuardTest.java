package com.paximum.paxassist.orchestrator.slot;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

import com.paximum.paxassist.ai.SlotCriteria;

import static org.assertj.core.api.Assertions.assertThat;

class SlotGuardTest {

    // Fixed "today" = 2026-07-08.
    private final SlotGuard guard =
            new SlotGuard(Clock.fixed(Instant.parse("2026-07-08T00:00:00Z"), ZoneOffset.UTC));
    private final ObjectMapper objectMapper = new ObjectMapper();

    private SlotCriteria slots(Map<String, Object> fields) {
        return objectMapper.convertValue(fields, SlotCriteria.class);
    }

    @Test
    void nullCriteria_returnsEmpty() {
        assertThat(guard.checkInvalidSlots(null)).isEmpty();
    }

    @Test
    void futureCheckIn_returnsEmpty() {
        assertThat(guard.checkInvalidSlots(slots(Map.of("checkIn", "2026-08-01")))).isEmpty();
    }

    @Test
    void today_isNotPast() {
        assertThat(guard.checkInvalidSlots(slots(Map.of("checkIn", "2026-07-08")))).isEmpty();
    }

    @Test
    void pastCheckIn_returnsFriendlyMessageWithTheDate() {
        Optional<String> msg = guard.checkInvalidSlots(slots(Map.of("checkIn", "2026-06-25")));
        assertThat(msg).isPresent();
        assertThat(msg.get()).contains("2026-06-25").contains("geçmiş");
    }

    @Test
    void pastDepartureDate_returnsMessage() {
        assertThat(guard.checkInvalidSlots(slots(Map.of("departureDate", "2026-07-07")))).isPresent();
    }

    @Test
    void futureCheckInButPastCheckOut_returnsMessage() {
        Optional<String> msg = guard.checkInvalidSlots(
                slots(Map.of("checkIn", "2026-08-01", "checkOut", "2026-06-01")));
        assertThat(msg).isPresent();
        assertThat(msg.get()).contains("2026-06-01");
    }

    @Test
    void checkOutBeforeCheckIn_returnsMessage() {
        Optional<String> msg = guard.checkInvalidSlots(
                slots(Map.of("checkIn", "2026-08-05", "checkOut", "2026-08-01")));
        assertThat(msg).isPresent();
        assertThat(msg.get()).contains("önce veya aynı olamaz");
    }

    @Test
    void checkOutSameAsCheckIn_returnsMessage() {
        Optional<String> msg = guard.checkInvalidSlots(
                slots(Map.of("checkIn", "2026-08-05", "checkOut", "2026-08-05")));
        assertThat(msg).isPresent();
        assertThat(msg.get()).contains("önce veya aynı olamaz");
    }

    @Test
    void returnDateBeforeDepartureDate_returnsMessage() {
        Optional<String> msg = guard.checkInvalidSlots(
                slots(Map.of("departureDate", "2026-08-05", "returnDate", "2026-08-01")));
        assertThat(msg).isPresent();
        assertThat(msg.get()).contains("önce olamaz");
    }

    @Test
    void unparsableDate_returnsMessage() {
        Optional<String> msg = guard.checkInvalidSlots(slots(Map.of("checkIn", "yarın")));
        assertThat(msg).isPresent();
        assertThat(msg.get()).contains("geçerli değil");
    }

    @Test
    void negativeAdults_returnsMessage() {
        Optional<String> msg = guard.checkInvalidSlots(slots(Map.of("adults", -1)));
        assertThat(msg).isPresent();
        assertThat(msg.get()).contains("Yetişkin sayısı en az 1 olmalıdır");
    }

    @Test
    void zeroAdults_returnsMessage() {
        Optional<String> msg = guard.checkInvalidSlots(slots(Map.of("adults", 0)));
        assertThat(msg).isPresent();
        assertThat(msg.get()).contains("Yetişkin sayısı en az 1 olmalıdır");
    }

    @Test
    void negativeChildren_returnsMessage() {
        Optional<String> msg = guard.checkInvalidSlots(slots(Map.of("children", -2)));
        assertThat(msg).isPresent();
        assertThat(msg.get()).contains("Çocuk sayısı negatif olamaz");
    }

    @Test
    void negativeChildAge_returnsMessage() {
        Optional<String> msg = guard.checkInvalidSlots(slots(Map.of("childAges", java.util.List.of(5, -1, 8))));
        assertThat(msg).isPresent();
        assertThat(msg.get()).contains("Çocuk yaşları negatif olamaz");
    }

    @Test
    void childAgeAbove17_returnsMessage() {
        // "çocuk 25 yaşında" → out of the 0-17 child range; must be rejected before search.
        Optional<String> msg = guard.checkInvalidSlots(slots(Map.of("childAges", java.util.List.of(5, 25))));
        assertThat(msg).isPresent();
        assertThat(msg.get()).contains("0-17");
    }

    @Test
    void childAge17IsAccepted() {
        // 17 is still a child; only 18+ is rejected.
        assertThat(guard.checkInvalidSlots(slots(Map.of("childAges", java.util.List.of(17))))).isEmpty();
    }

    @Test
    void zeroOrNegativeRooms_returnsMessage() {
        assertThat(guard.checkInvalidSlots(slots(Map.of("rooms", 0)))).isPresent();
        assertThat(guard.checkInvalidSlots(slots(Map.of("rooms", -1)))).isPresent();
    }

    @Test
    void negativeBudget_returnsMessage() {
        assertThat(guard.checkInvalidSlots(slots(Map.of("hotelMaxPrice", -100)))).isPresent();
        assertThat(guard.checkInvalidSlots(slots(Map.of("flightMaxPrice", 0)))).isPresent();
    }
}
