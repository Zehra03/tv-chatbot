package com.paximum.paxassist.orchestrator.mapper;

import java.util.Map;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

import com.paximum.paxassist.ai.SlotCriteria;
import com.paximum.paxassist.hotel.dto.HotelSearchRequest;

import static org.assertj.core.api.Assertions.assertThat;

class HotelCriteriaMapperTest {

    private final HotelCriteriaMapper mapper = new HotelCriteriaMapper();
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
    void nightIsNullWhenCheckOutMissing() {
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
