package com.paximum.paxassist.flight.service;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.paximum.paxassist.flight.domain.FlightProduct;
import com.paximum.paxassist.flight.domain.FlightSearchCriteria;
import com.paximum.paxassist.flight.domain.PassengerCount;
import com.paximum.paxassist.flight.domain.TripType;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Covers the {@code nonstop} / {@code preferredAirline} filters through {@link MockFlightSearchService},
 * whose fixed data (stops 0/1/0) exercises the filter and its wiring into the search use-case at once.
 */
class FlightResultFilterTest {

    private final MockFlightSearchService service = new MockFlightSearchService();

    private FlightSearchCriteria.FlightSearchCriteriaBuilder criteria() {
        return FlightSearchCriteria.builder()
                .origin("IST")
                .destination("AYT")
                .departDate(LocalDate.of(2026, 8, 10))
                .tripType(TripType.ONE_WAY)
                .passengers(PassengerCount.builder().adults(1).children(0).infants(0).build())
                .currency("EUR");
    }

    @Test
    void search_withoutFilters_returnsEveryFlight() {
        List<FlightProduct> results = service.search(criteria().build()).results();

        assertThat(results).extracting(FlightProduct::getId).containsExactly("FLT-1", "FLT-2", "FLT-3");
    }

    @Test
    void search_nonstop_dropsFlightsWithStops() {
        List<FlightProduct> results = service.search(criteria().nonstop(true).build()).results();

        assertThat(results).extracting(FlightProduct::getId).containsExactly("FLT-1", "FLT-3");
        assertThat(results).allMatch(product -> product.getStops() == 0);
    }

    @Test
    void search_nonstopFalse_keepsConnectingFlights() {
        List<FlightProduct> results = service.search(criteria().nonstop(false).build()).results();

        assertThat(results).extracting(FlightProduct::getId).containsExactly("FLT-1", "FLT-2", "FLT-3");
    }

    @Test
    void search_preferredAirline_keepsOnlyThatCarrier() {
        List<FlightProduct> results = service.search(criteria().preferredAirline("pegasus").build()).results();

        assertThat(results).extracting(FlightProduct::getAirline).containsExactly("Pegasus");
    }

    @Test
    void search_combinesNonstopAndAirline() {
        List<FlightProduct> results = service.search(
                criteria().nonstop(true).preferredAirline("Pegasus").build()).results();

        assertThat(results).isEmpty();
    }
}
