package com.paximum.paxassist.flight.domain;

import java.time.LocalDate;
import java.time.LocalTime;
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
                .departTimeFrom(LocalTime.of(8, 0))
                .departTimeTo(LocalTime.of(12, 0))
                .passengers(PassengerCount.builder().adults(2).children(1).infants(0).build())
                .build();

        String cacheKey = criteria.toCacheKey();

        assertThat(cacheKey)
                .isEqualTo("IST|LHR|2026-08-10|2026-08-20|ROUND_TRIP|USD|true|TK|08:00|12:00|2A1C0I");
    }

    @Test
    void toCacheKey_separatesDifferentDepartureWindows() {
        // The window narrows the cached result set, so two windows must not share one cache entry.
        FlightSearchCriteria.FlightSearchCriteriaBuilder base = FlightSearchCriteria.builder()
                .origin("IST")
                .destination("LHR")
                .departDate(LocalDate.of(2026, 8, 10))
                .tripType(TripType.ONE_WAY)
                .currency("USD")
                .passengers(PassengerCount.builder().adults(1).children(0).infants(0).build());

        String morning = base.departTimeFrom(LocalTime.of(6, 0)).departTimeTo(LocalTime.of(12, 0))
                .build().toCacheKey();
        String evening = base.departTimeFrom(LocalTime.of(18, 0)).departTimeTo(LocalTime.of(23, 0))
                .build().toCacheKey();

        assertThat(morning).isNotEqualTo(evening);
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

    /**
     * The REST boundary rejects a same-city route with a 400 (FlightSearchApiRequest's @AssertTrue),
     * but the chat path builds criteria straight from slot-filling and never touches that DTO — so
     * the rule has to hold here too, otherwise "İstanbul'dan İstanbul'a uçuş" reaches the provider.
     */
    @Test
    void missingRequiredFields_reportsDestination_whenRouteStartsAndEndsInTheSameCity() {
        FlightSearchCriteria criteria = sameCityCriteria("İstanbul", "İstanbul");

        assertThat(criteria.missingRequiredFields()).containsExactly("destination");
    }

    @Test
    void missingRequiredFields_treatsSameCityAsSame_ignoringCaseAndSurroundingSpace() {
        // Chat fills these from free text, so casing/padding varies turn to turn.
        assertThat(sameCityCriteria("istanbul", "İSTANBUL ").missingRequiredFields())
                .containsExactly("destination");
        assertThat(sameCityCriteria("IZMIR", "izmir").missingRequiredFields())
                .containsExactly("destination");
        // The four Turkish i-variants must all fold together: users type "ızmır" and "İzmir" alike,
        // and neither the Turkish nor the root locale matches both spellings on its own.
        assertThat(sameCityCriteria("ızmır", "izmir").missingRequiredFields())
                .containsExactly("destination");
        assertThat(sameCityCriteria("İzmir", "IZMIR").missingRequiredFields())
                .containsExactly("destination");
    }

    @Test
    void missingRequiredFields_allowsDistinctCities() {
        assertThat(sameCityCriteria("İstanbul", "Ankara").missingRequiredFields()).isEmpty();
    }

    private FlightSearchCriteria sameCityCriteria(String origin, String destination) {
        return FlightSearchCriteria.builder()
                .origin(origin)
                .destination(destination)
                .departDate(LocalDate.of(2026, 8, 10))
                .tripType(TripType.ONE_WAY)
                .passengers(PassengerCount.builder().adults(1).build())
                .currency("TRY")
                .build();
    }
}
