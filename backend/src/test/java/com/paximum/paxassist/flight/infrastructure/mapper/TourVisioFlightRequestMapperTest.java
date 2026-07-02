package com.paximum.paxassist.flight.infrastructure.mapper;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;

import com.paximum.paxassist.flight.config.TourVisioProperties;
import com.paximum.paxassist.flight.domain.FlightSearchCriteria;
import com.paximum.paxassist.flight.domain.PassengerCount;
import com.paximum.paxassist.flight.domain.TripType;
import com.paximum.paxassist.flight.infrastructure.dto.request.TourVisioPriceSearchRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TourVisioFlightRequestMapperTest {

    private final TourVisioFlightRequestMapper mapper = new TourVisioFlightRequestMapper(
            new TourVisioProperties("https://test.example.com", "en-US", "Europe/Istanbul", "a", "u", "p"));

    private FlightSearchCriteria.FlightSearchCriteriaBuilder baseCriteria() {
        return FlightSearchCriteria.builder()
                .origin("IST")
                .destination("LHR")
                .departDate(LocalDate.of(2026, 8, 10))
                .currency("USD")
                .passengers(PassengerCount.builder().adults(1).children(0).infants(0).build());
    }

    @Test
    void toRequest_throwsWhenReturnDateBeforeDepartDate() {
        FlightSearchCriteria criteria = baseCriteria()
                .tripType(TripType.ROUND_TRIP)
                .returnDate(LocalDate.of(2026, 8, 5))
                .build();

        assertThatThrownBy(() -> mapper.toRequest(criteria))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("returnDate must be on or after departDate");
    }

    @Test
    void toRequest_throwsWhenRoundTripMissingReturnDate() {
        FlightSearchCriteria criteria = baseCriteria()
                .tripType(TripType.ROUND_TRIP)
                .build();

        assertThatThrownBy(() -> mapper.toRequest(criteria))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("returnDate is required");
    }

    @Test
    void toRequest_computesNightsForValidRoundTrip() {
        FlightSearchCriteria criteria = baseCriteria()
                .tripType(TripType.ROUND_TRIP)
                .returnDate(LocalDate.of(2026, 8, 15))
                .build();

        TourVisioPriceSearchRequest request = mapper.toRequest(criteria);

        assertThat(request.night()).isEqualTo(5);
    }

    @Test
    void toRequest_leavesNightNullForOneWay() {
        FlightSearchCriteria criteria = baseCriteria()
                .tripType(TripType.ONE_WAY)
                .build();

        TourVisioPriceSearchRequest request = mapper.toRequest(criteria);

        assertThat(request.night()).isNull();
    }
}
