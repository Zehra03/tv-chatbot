package com.paximum.paxassist.hotel;

import org.junit.jupiter.api.Test;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TourVisioHotelApiClientTest {

    private final TourVisioHotelApiClient mockClient = new MockTourVisioHotelApiClient();

    @Test
    void shouldReturnMockHotelsSuccessfully() {
        HotelSearchCriteria criteria = new HotelSearchCriteria("Antalya");
        
        List<HotelProduct> result = mockClient.searchHotels(criteria);

        assertThat(result).isNotNull();
        assertThat(result).hasSize(6);
        assertThat(result.get(0).hotelName()).isEqualTo("Rixos Premium");
    }

    @Test
    void shouldImplementClientInterfaceCorrectly() {
        // As the actual WebClient/RestTemplate implementation is developed by the friend,
        // this test suite will be expanded to mock HTTP interactions and verify Auth headers.
        // Example for future:
        /*
        // when(restTemplate.exchange(eq(API_URL), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
        //      .thenReturn(mockResponse);
        // ...
        */
        assertThat(TourVisioHotelApiClient.class).isInterface();
    }
}
