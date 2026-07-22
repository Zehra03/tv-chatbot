package com.paximum.paxassist.chat.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.paximum.paxassist.ai.SlotCriteria;
import com.paximum.paxassist.chat.dto.PartialCriteriaDto;
import com.paximum.paxassist.chat.dto.ResultCardDto;
import com.paximum.paxassist.flight.domain.FlightProduct;
import com.paximum.paxassist.hotel.HotelProduct;

import static org.assertj.core.api.Assertions.assertThat;

class ChatViewMapperTest {

    private final ChatViewMapper mapper = new ChatViewMapper();

    /**
     * Builds the accumulated map the way production does — {@code SlotFillingService.accumulate}
     * round-trips the whole record through {@code convertValue}, so EVERY field lands in the map,
     * nulls included. Hand-written sparse maps do not reproduce that shape and let key-presence
     * bugs through.
     */
    private static Map<String, Object> accumulatedOf(SlotCriteria criteria) {
        return new ObjectMapper().convertValue(criteria, new TypeReference<Map<String, Object>>() {
        });
    }

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
    void infersHotelFromRealSlotShapeWhereEveryFlightKeyIsPresentButNull() {
        // Regression: a pure hotel search, serialized exactly as the session stores it.
        SlotCriteria hotelSearch = new SlotCriteria(
                "Antalya", "2026-08-01", "2026-08-05", null, 1, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null, null, null,
                2, 0, null, "TR", "EUR",
                null, null, null);

        Map<String, Object> accumulated = accumulatedOf(hotelSearch);
        // The map really does carry the flight keys — that is what broke containsKey inference.
        assertThat(accumulated).containsKey("origin").containsEntry("origin", null);

        PartialCriteriaDto criteria = mapper.toPartialCriteria(accumulated, null);

        assertThat(criteria.intent()).isEqualTo("hotel");
        assertThat(criteria.criteria())
                .containsEntry("destination", "Antalya")
                .containsEntry("checkIn", "2026-08-01")
                .doesNotContainKeys("origin", "departDate");
    }

    @Test
    void infersFlightFromRealSlotShapeWhereEveryHotelKeyIsPresentButNull() {
        SlotCriteria flightSearch = new SlotCriteria(
                null, null, null, null, null, null, null, null, null, null,
                "IST", "LHR", "2026-08-01", null, null, null, null, null, null, null, null, null,
                1, 0, null, "TR", "EUR",
                null, null, null);

        PartialCriteriaDto criteria = mapper.toPartialCriteria(accumulatedOf(flightSearch), null);

        assertThat(criteria.intent()).isEqualTo("flight");
        assertThat(criteria.criteria())
                .containsEntry("origin", "IST")
                .containsEntry("departDate", "2026-08-01");
    }

    @Test
    void doesNotInferFromSharedFieldsAlone() {
        // adults/nationality/currency belong to both domains → no honest guess.
        SlotCriteria sharedOnly = new SlotCriteria(
                null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null, null, null,
                2, 0, null, "TR", "EUR",
                null, null, null);

        assertThat(mapper.toPartialCriteria(accumulatedOf(sharedOnly), null)).isNull();
    }

    /**
     * HATA 5 (fixed): the flight search form used to carry a single {@code passengers} count, so this
     * mapper folded the chat slot map's {@code adults}/{@code children} into that one number and
     * dropped {@code childAges} entirely — a 2-adult-1-child chat search reached the reservation form
     * as "3 adults", and the child's age was lost. The frontend's {@code FlightSearchCriteria} now
     * carries {@code adults} + {@code childAges} directly (the same shape the hotel criteria always
     * had), so both pass through unchanged like every other shared field.
     */
    @Test
    void leavesFlightAdultsAndChildAgesAlone() {
        SlotCriteria twoAdultsOneChild = flightSlots(2, 1, List.of(6));

        PartialCriteriaDto criteria = mapper.toPartialCriteria(accumulatedOf(twoAdultsOneChild), "flight");

        assertThat(criteria.criteria())
                .containsEntry("adults", 2)
                .containsEntry("childAges", List.of(6));
    }

    @Test
    void leavesHotelAdultsAndChildrenAlone() {
        SlotCriteria hotelSearch = new SlotCriteria(
                "Antalya", "2026-08-01", "2026-08-05", null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null, null, null,
                2, 1, List.of(7), "TR", "EUR",
                null, null, null);

        PartialCriteriaDto criteria = mapper.toPartialCriteria(accumulatedOf(hotelSearch), "hotel");

        assertThat(criteria.criteria())
                .containsEntry("adults", 2)
                .containsEntry("children", 1)
                .containsEntry("childAges", List.of(7));
    }

    private static SlotCriteria flightSlots(Integer adults, Integer children, List<Integer> childAges) {
        return new SlotCriteria(
                null, null, null, null, null, null, null, null, null, null,
                "IST", "LHR", "2026-08-01", null, null, null, null, null, null, null, null, null,
                adults, children, childAges, "TR", "EUR",
                null, null, null);
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
