package com.paximum.paxassist.mcp;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.paximum.paxassist.flight.domain.FlightSearchCriteria;
import com.paximum.paxassist.flight.domain.TripType;
import com.paximum.paxassist.flight.service.FlightSearchOutcome;
import com.paximum.paxassist.flight.service.FlightSearchService;
import com.paximum.paxassist.hotel.HotelSearchService;
import com.paximum.paxassist.hotel.dto.HotelSearchRequest;
import com.paximum.paxassist.hotel.dto.HotelSearchResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HotelFlightToolsTest {

    @Mock
    private HotelSearchService hotelSearchService;
    @Mock
    private FlightSearchService flightSearchService;
    @InjectMocks
    private HotelFlightTools tools;

    @Test
    void searchHotels_delegatesWithMappedRequest() {
        HotelSearchResponse response = HotelSearchResponse.success(List.of());
        ArgumentCaptor<HotelSearchRequest> captor = ArgumentCaptor.forClass(HotelSearchRequest.class);
        when(hotelSearchService.searchHotels(captor.capture())).thenReturn(response);

        HotelSearchResponse result = tools.searchHotels("Antalya", "2026-08-01", 4, 2, List.of(5), "TR", "TRY");

        assertThat(result).isSameAs(response);
        HotelSearchRequest request = captor.getValue();
        assertThat(request.destination()).isEqualTo("Antalya");
        assertThat(request.checkIn()).isEqualTo("2026-08-01");
        assertThat(request.night()).isEqualTo(4);
        assertThat(request.adult()).isEqualTo(2);
    }

    @Test
    void searchFlights_oneWayWhenNoReturnDate() {
        FlightSearchOutcome outcome = FlightSearchOutcome.complete(List.of());
        ArgumentCaptor<FlightSearchCriteria> captor = ArgumentCaptor.forClass(FlightSearchCriteria.class);
        when(flightSearchService.search(captor.capture())).thenReturn(outcome);

        FlightSearchOutcome result = tools.searchFlights("IST", "CDG", "2026-08-01", null, 2, List.of(8), "TRY");

        assertThat(result).isSameAs(outcome);
        FlightSearchCriteria criteria = captor.getValue();
        assertThat(criteria.getTripType()).isEqualTo(TripType.ONE_WAY);
        assertThat(criteria.getDepartDate()).isEqualTo(LocalDate.of(2026, 8, 1));
        assertThat(criteria.getPassengers().getAdults()).isEqualTo(2);
        assertThat(criteria.getPassengers().getChildren()).isEqualTo(1);
    }

    @Test
    void searchFlights_typesChildrenByAge() {
        ArgumentCaptor<FlightSearchCriteria> captor = ArgumentCaptor.forClass(FlightSearchCriteria.class);
        when(flightSearchService.search(captor.capture())).thenReturn(FlightSearchOutcome.complete(List.of()));

        tools.searchFlights("IST", "CDG", "2026-08-01", null, 1, List.of(1, 8, 12), "TRY");

        FlightSearchCriteria criteria = captor.getValue();
        assertThat(criteria.getPassengers().getInfants()).isEqualTo(1);
        assertThat(criteria.getPassengers().getChildren()).isEqualTo(1);
        assertThat(criteria.getPassengers().getAdults()).isEqualTo(2);
    }

    @Test
    void searchFlights_roundTripWhenReturnDatePresent() {
        ArgumentCaptor<FlightSearchCriteria> captor = ArgumentCaptor.forClass(FlightSearchCriteria.class);
        when(flightSearchService.search(captor.capture())).thenReturn(FlightSearchOutcome.complete(List.of()));

        tools.searchFlights("IST", "CDG", "2026-08-01", "2026-08-10", 1, null, "EUR");

        FlightSearchCriteria criteria = captor.getValue();
        assertThat(criteria.getTripType()).isEqualTo(TripType.ROUND_TRIP);
        assertThat(criteria.getReturnDate()).isEqualTo(LocalDate.of(2026, 8, 10));
    }
}
