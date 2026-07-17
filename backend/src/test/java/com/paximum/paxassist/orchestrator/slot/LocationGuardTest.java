package com.paximum.paxassist.orchestrator.slot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.mock;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.paximum.paxassist.ai.SlotCriteria;
import com.paximum.paxassist.flight.domain.FlightLocation;
import com.paximum.paxassist.flight.service.FlightLocationService;
import com.paximum.paxassist.hotel.HotelSearchService;
import com.paximum.paxassist.hotel.dto.HotelLocationDto;

@ExtendWith(MockitoExtension.class)
class LocationGuardTest {

    @Mock
    private HotelSearchService hotelSearchService;

    @Mock
    private FlightLocationService flightLocationService;

    private LocationGuard guard;

    @BeforeEach
    void setUp() {
        guard = new LocationGuard(hotelSearchService, flightLocationService);
    }

    @Test
    void checkInvalidLocation_nullCriteria_returnsEmpty() {
        assertThat(guard.checkInvalidLocation(null, "HOTEL")).isEmpty();
    }

    @Test
    void checkInvalidLocation_hotel_validLocation_returnsEmpty() {
        SlotCriteria criteria = SlotCriteria.empty();
        criteria = new SlotCriteria("Antalya", null, null, null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null, null, null, null, null);

        when(hotelSearchService.suggestLocations("Antalya"))
                .thenReturn(List.of(new HotelLocationDto("123", "Antalya", "city")));

        Optional<String> result = guard.checkInvalidLocation(criteria, "HOTEL");

        assertThat(result).isEmpty();
    }

    @Test
    void checkInvalidLocation_hotel_invalidLocation_returnsMessage() {
        SlotCriteria criteria = SlotCriteria.empty();
        // Since SlotCriteria is a record, creating one with only location
        criteria = new SlotCriteria("burdan", null, null, null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null, null, null, null, null);

        when(hotelSearchService.suggestLocations("burdan"))
                .thenReturn(List.of());

        Optional<String> result = guard.checkInvalidLocation(criteria, "HOTEL");

        assertThat(result).isPresent();
        assertThat(result.get()).contains("sistemimizde bulunamadı");
    }

    @Test
    void checkInvalidLocation_flight_invalidOrigin_returnsMessage() {
        SlotCriteria criteria = new SlotCriteria(null, null, null, null, null, null, null, null, null, null, "burdan",
                "Antalya", null, null, null, null, null, null, null, null, null, null, null, null, null, null, null);

        when(flightLocationService.suggest("burdan", true))
                .thenReturn(List.of());

        Optional<String> result = guard.checkInvalidLocation(criteria, "FLIGHT");

        assertThat(result).isPresent();
        assertThat(result.get()).contains("kalkış noktası");
    }
}
