package com.paximum.paxassist.flight.mapper;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.paximum.paxassist.flight.domain.FlightProduct;
import com.paximum.paxassist.flight.domain.FlightSearchCriteria;
import com.paximum.paxassist.flight.domain.TripType;
import com.paximum.paxassist.flight.dto.FlightSearchRequestDto;
import com.paximum.paxassist.flight.dto.FlightSearchResponseDto;
import com.paximum.paxassist.flight.dto.FlightSearchStatus;
import com.paximum.paxassist.flight.dto.PassengerCountDto;
import com.paximum.paxassist.flight.service.FlightSearchOutcome;

import static org.assertj.core.api.Assertions.assertThat;

class FlightSearchMapperTest {

    private final FlightSearchMapper mapper = new FlightSearchMapper();

    @Test
    void toDomain_mapsCorrectly() {
        FlightSearchRequestDto dto = new FlightSearchRequestDto(
                "IST", "LHR", LocalDate.of(2026, 8, 10), LocalDate.of(2026, 8, 20),
                TripType.ROUND_TRIP, new PassengerCountDto(2, 1, 0), "USD", true, "TK");

        FlightSearchCriteria criteria = mapper.toDomain(dto);

        assertThat(criteria.getOrigin()).isEqualTo("IST");
        assertThat(criteria.getDestination()).isEqualTo("LHR");
        assertThat(criteria.getDepartDate()).isEqualTo(LocalDate.of(2026, 8, 10));
        assertThat(criteria.getReturnDate()).isEqualTo(LocalDate.of(2026, 8, 20));
        assertThat(criteria.getTripType()).isEqualTo(TripType.ROUND_TRIP);
        assertThat(criteria.getPassengers().getAdults()).isEqualTo(2);
        assertThat(criteria.getPassengers().getChildren()).isEqualTo(1);
        assertThat(criteria.getPassengers().getInfants()).isEqualTo(0);
        assertThat(criteria.getCurrency()).isEqualTo("USD");
        assertThat(criteria.getNonstop()).isTrue();
        assertThat(criteria.getPreferredAirline()).isEqualTo("TK");
    }

    @Test
    void toDomain_handlesNullPassengers() {
        FlightSearchRequestDto dto = new FlightSearchRequestDto(
                "IST", "LHR", LocalDate.of(2026, 8, 10), null,
                TripType.ONE_WAY, null, "USD", null, null);

        FlightSearchCriteria criteria = mapper.toDomain(dto);

        assertThat(criteria.getPassengers()).isNull();
    }

    @Test
    void toResponse_mapsIncompleteOutcome() {
        FlightSearchOutcome outcome = FlightSearchOutcome.incomplete(List.of("origin", "destination"));

        FlightSearchResponseDto response = mapper.toResponse(outcome);

        assertThat(response.status()).isEqualTo(FlightSearchStatus.NEEDS_MORE_INFO);
        assertThat(response.missingFields()).containsExactly("origin", "destination");
        assertThat(response.results()).isEmpty();
    }

    @Test
    void toResponse_mapsCompleteOutcome() {
        FlightProduct product = FlightProduct.builder()
                .id("p1")
                .airline("TK")
                .flightNumber("TK1979")
                .origin("IST")
                .destination("LHR")
                .departTime(java.time.Instant.parse("2026-08-10T10:00:00Z"))
                .arriveTime(java.time.Instant.parse("2026-08-10T12:00:00Z"))
                .stops(0)
                .baggage("20kg")
                .price(java.math.BigDecimal.valueOf(150.0))
                .currency("USD")
                .build();
        FlightSearchOutcome outcome = FlightSearchOutcome.complete(List.of(product));

        FlightSearchResponseDto response = mapper.toResponse(outcome);

        assertThat(response.status()).isEqualTo(FlightSearchStatus.COMPLETE);
        assertThat(response.missingFields()).isEmpty();
        assertThat(response.results()).hasSize(1);
        assertThat(response.results().get(0).id()).isEqualTo("p1");
        assertThat(response.results().get(0).airline()).isEqualTo("TK");
        assertThat(response.results().get(0).flightNumber()).isEqualTo("TK1979");
    }
}
