package com.paximum.paxassist.orchestrator.mapper;

import java.util.Map;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

import com.paximum.paxassist.ai.SlotCriteria;
import com.paximum.paxassist.hotel.dto.HotelSearchRequest;

import static org.assertj.core.api.Assertions.assertThat;

class HotelCriteriaMapperTest {

    private final HotelCriteriaMapper mapper = new HotelCriteriaMapper(new GeoCountryResolver());
    private final ObjectMapper objectMapper = new ObjectMapper();

    private SlotCriteria slots(Map<String, Object> fields) {
        return objectMapper.convertValue(fields, SlotCriteria.class);
    }

    @Test
    void computesNightsFromCheckInAndCheckOut() {
        SlotCriteria criteria = slots(Map.of(
                "location", "Antalya",
                "checkIn", "2026-08-01",
                "checkOut", "2026-08-05",
                "adults", 2));

        HotelSearchRequest request = mapper.toRequest(criteria);

        assertThat(request.destination()).isEqualTo("Antalya");
        assertThat(request.checkIn()).isEqualTo("2026-08-01");
        assertThat(request.night()).isEqualTo(4);
        assertThat(request.adult()).isEqualTo(2);
    }

    @Test
    void usesExplicitNightsWhenCheckOutMissing() {
        // User answered "5 gece" instead of a checkout date — the count must map straight to night.
        SlotCriteria criteria = slots(Map.of(
                "location", "Antalya",
                "checkIn", "2026-08-08",
                "nights", 5,
                "adults", 1));

        HotelSearchRequest request = mapper.toRequest(criteria);

        assertThat(request.night()).isEqualTo(5);
    }

    @Test
    void checkOutSpanWinsOverExplicitNights() {
        // Both present: the precise date span is authoritative, the loose count is ignored.
        SlotCriteria criteria = slots(Map.of(
                "checkIn", "2026-08-01",
                "checkOut", "2026-08-05",
                "nights", 9));

        assertThat(mapper.toRequest(criteria).night()).isEqualTo(4);
    }

    @Test
    void nightIsNullWhenNeitherCheckOutNorNightsGiven() {
        SlotCriteria criteria = slots(Map.of("location", "Antalya", "checkIn", "2026-08-01"));

        HotelSearchRequest request = mapper.toRequest(criteria);

        assertThat(request.night()).isNull();
    }

    @Test
    void nightIsNullWhenDatesUnparseable() {
        SlotCriteria criteria = slots(Map.of("checkIn", "yarın", "checkOut", "haftaya"));

        assertThat(mapper.toRequest(criteria).night()).isNull();
    }

    @Test
    void appliesRequestDefaultsForNationalityAndCurrency() {
        HotelSearchRequest request = mapper.toRequest(slots(Map.of("location", "Antalya")));

        // HotelSearchRequest's compact constructor fills these when null.
        assertThat(request.nationality()).isEqualTo("TR");
        assertThat(request.currency()).isEqualTo("TRY");
        assertThat(request.culture()).isEqualTo("tr-TR");
    }
}
