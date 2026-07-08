package com.paximum.paxassist.orchestrator.mapper;

import java.time.LocalDate;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

import com.paximum.paxassist.ai.SlotCriteria;
import com.paximum.paxassist.flight.domain.FlightSearchCriteria;
import com.paximum.paxassist.flight.domain.TripType;

import static org.assertj.core.api.Assertions.assertThat;

class FlightCriteriaMapperTest {

    private final FlightCriteriaMapper mapper = new FlightCriteriaMapper();
    private final ObjectMapper objectMapper = new ObjectMapper();

    private SlotCriteria slots(Map<String, Object> fields) {
        return objectMapper.convertValue(fields, SlotCriteria.class);
    }

    @Test
    void oneWayWhenNoReturnDate() {
        FlightSearchCriteria criteria = mapper.toCriteria(slots(Map.of(
                "origin", "IST", "destination", "CDG", "departureDate", "2026-08-01", "adults", 1)));

        assertThat(criteria.getTripType()).isEqualTo(TripType.ONE_WAY);
        assertThat(criteria.getDepartDate()).isEqualTo(LocalDate.of(2026, 8, 1));
        assertThat(criteria.getReturnDate()).isNull();
    }

    @Test
    void roundTripWhenReturnDatePresent() {
        FlightSearchCriteria criteria = mapper.toCriteria(slots(Map.of(
                "origin", "IST", "destination", "CDG",
                "departureDate", "2026-08-01", "returnDate", "2026-08-10", "adults", 2)));

        assertThat(criteria.getTripType()).isEqualTo(TripType.ROUND_TRIP);
        assertThat(criteria.getReturnDate()).isEqualTo(LocalDate.of(2026, 8, 10));
    }

    @Test
    void buildsPassengersFromAdultsAndChildren() {
        FlightSearchCriteria criteria = mapper.toCriteria(slots(Map.of("adults", 2, "children", 1)));

        assertThat(criteria.getPassengers()).isNotNull();
        assertThat(criteria.getPassengers().getAdults()).isEqualTo(2);
        assertThat(criteria.getPassengers().getChildren()).isEqualTo(1);
        assertThat(criteria.getPassengers().getInfants()).isZero();
    }

    @Test
    void passengersNullWhenNoAdults_soFlightModuleReportsItMissing() {
        FlightSearchCriteria criteria = mapper.toCriteria(slots(Map.of("origin", "IST")));

        assertThat(criteria.getPassengers()).isNull();
        assertThat(criteria.missingRequiredFields()).contains("passengers");
    }
}
