package com.paximum.paxassist.flight.domain;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FlightSearchCriteriaTest {

    @Test
    void toCacheKey_formatsCorrectly() {
        FlightSearchCriteria criteria = FlightSearchCriteria.builder()
                .origin("IST")
                .destination("LHR")
                .departDate(LocalDate.of(2026, 8, 10))
                .returnDate(LocalDate.of(2026, 8, 20))
                .tripType(TripType.ROUND_TRIP)
                .currency("USD")
                .nonstop(true)
                .preferredAirline("TK")
                .passengers(PassengerCount.builder().adults(2).children(1).infants(0).build())
                .build();

        String cacheKey = criteria.toCacheKey();

        assertThat(cacheKey).isEqualTo("IST|LHR|2026-08-10|2026-08-20|ROUND_TRIP|USD|true|TK|2A1C0I");
    }

    @Test
    void toCacheKey_handlesNullPassengers() {
        FlightSearchCriteria criteria = FlightSearchCriteria.builder()
                .origin("IST")
                .destination("LHR")
                .build();

        String cacheKey = criteria.toCacheKey();

        assertThat(cacheKey).endsWith("|0A0C0I");
    }

    @Test
    void missingRequiredFields_returnsAllMissingFields_whenEmpty() {
        FlightSearchCriteria criteria = FlightSearchCriteria.builder().build();

        List<String> missing = criteria.missingRequiredFields();

        assertThat(missing).containsExactlyInAnyOrder(
                "origin", "destination", "departDate", "tripType", "passengers", "currency"
        );
    }

    @Test
    void missingRequiredFields_returnsEmpty_whenAllRequiredFieldsPresent() {
        FlightSearchCriteria criteria = FlightSearchCriteria.builder()
                .origin("IST")
                .destination("LHR")
                .departDate(LocalDate.of(2026, 8, 10))
                .tripType(TripType.ONE_WAY)
                .passengers(PassengerCount.builder().adults(1).build())
                .currency("USD")
                .build();

        List<String> missing = criteria.missingRequiredFields();

        assertThat(missing).isEmpty();
    }

    @Test
    void missingRequiredFields_requiresReturnDate_forRoundTrip() {
        FlightSearchCriteria criteria = FlightSearchCriteria.builder()
                .origin("IST")
                .destination("LHR")
                .departDate(LocalDate.of(2026, 8, 10))
                .tripType(TripType.ROUND_TRIP)
                .passengers(PassengerCount.builder().adults(1).build())
                .currency("USD")
                .build();

        List<String> missing = criteria.missingRequiredFields();

        assertThat(missing).containsExactly("returnDate");
    }

    @Test
    void missingRequiredFields_requiresAtLeastOneAdult() {
        FlightSearchCriteria criteria = FlightSearchCriteria.builder()
                .origin("IST")
                .destination("LHR")
                .departDate(LocalDate.of(2026, 8, 10))
                .tripType(TripType.ONE_WAY)
                .passengers(PassengerCount.builder().adults(0).build())
                .currency("USD")
                .build();

        List<String> missing = criteria.missingRequiredFields();

        assertThat(missing).containsExactly("passengers");
    }
}
