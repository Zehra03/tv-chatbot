package com.paximum.paxassist.chat.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.paximum.paxassist.chat.dto.PartialCriteriaDto;
import com.paximum.paxassist.chat.dto.ResultCardDto;
import com.paximum.paxassist.flight.domain.FlightProduct;
import com.paximum.paxassist.hotel.HotelProduct;

import static org.assertj.core.api.Assertions.assertThat;

class ChatViewMapperTest {

    private final ChatViewMapper mapper = new ChatViewMapper();

    @Test
    void typesLiveProductsByRuntimeClass() {
        HotelProduct hotel = new HotelProduct("H1", "Rixos", "Antalya", 5, new BigDecimal("1500"), "EUR", "AI", true);
        FlightProduct flight = FlightProduct.builder().id("F1").airline("TK").price(new BigDecimal("120")).build();

        List<ResultCardDto> cards = mapper.toResultCards(List.of(hotel, flight));

        assertThat(cards).extracting(ResultCardDto::productType).containsExactly("hotel", "flight");
        assertThat(cards.get(0).product()).isSameAs(hotel);
        assertThat(cards.get(1).product()).isSameAs(flight);
    }

    @Test
    void typesJsonbRestoredCardsByDisjointKeys() {
        // After a restart the cards come back as generic maps (jsonb), not typed products.
        Map<String, Object> hotelMap = Map.of("id", "H1", "hotelName", "Rixos", "stars", 5);
        Map<String, Object> flightMap = Map.of("id", "F1", "airline", "TK", "departTime", "2026-08-01T09:00:00Z");

        List<ResultCardDto> cards = mapper.toResultCards(List.of(hotelMap, flightMap));

        assertThat(cards).extracting(ResultCardDto::productType).containsExactly("hotel", "flight");
    }

    @Test
    void renamesHotelSlotKeysAndDropsUnknownOnes() {
        Map<String, Object> accumulated = new LinkedHashMap<>();
        accumulated.put("location", "Antalya");     // → destination
        accumulated.put("checkIn", "2026-08-01");
        accumulated.put("adults", 2);
        accumulated.put("stars", 4);                 // dropped (not a frontend chip key)
        accumulated.put("boardType", "AI");          // dropped

        PartialCriteriaDto criteria = mapper.toPartialCriteria(accumulated, null);

        assertThat(criteria.intent()).isEqualTo("hotel");
        assertThat(criteria.criteria())
                .containsEntry("destination", "Antalya")
                .containsEntry("checkIn", "2026-08-01")
                .containsEntry("adults", 2)
                .doesNotContainKeys("location", "stars", "boardType");
    }

    @Test
    void renamesFlightDepartureDateAndInfersFlightIntent() {
        Map<String, Object> accumulated = new LinkedHashMap<>();
        accumulated.put("origin", "IST");
        accumulated.put("destination", "LHR");
        accumulated.put("departureDate", "2026-08-01"); // → departDate

        PartialCriteriaDto criteria = mapper.toPartialCriteria(accumulated, null);

        assertThat(criteria.intent()).isEqualTo("flight");
        assertThat(criteria.criteria())
                .containsEntry("origin", "IST")
                .containsEntry("destination", "LHR")
                .containsEntry("departDate", "2026-08-01")
                .doesNotContainKey("departureDate");
    }

    @Test
    void explicitDomainWinsOverInference() {
        PartialCriteriaDto criteria = mapper.toPartialCriteria(Map.of("location", "Antalya"), "hotel");
        assertThat(criteria.intent()).isEqualTo("hotel");
    }

    @Test
    void nullWhenNoCriteriaOrUnknownDomain() {
        assertThat(mapper.toPartialCriteria(Map.of(), "hotel")).isNull();
        assertThat(mapper.toPartialCriteria(null, "hotel")).isNull();
        // only unknown keys + no domain hint → cannot label → null
        assertThat(mapper.toPartialCriteria(Map.of("selectionReference", "1"), null)).isNull();
    }
}
