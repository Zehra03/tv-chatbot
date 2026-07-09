package com.paximum.paxassist.hotel;

import com.paximum.paxassist.hotel.dto.AutocompleteResponse;
import com.paximum.paxassist.hotel.dto.HotelSearchRequest;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

class MockTourVisioHotelApiClientTest {

    @Test
    void searchHotels_ReturnsMockListWithFullyPopulatedFields() {
        MockTourVisioHotelApiClient client = new MockTourVisioHotelApiClient();
        HotelSearchCriteria criteria = new HotelSearchCriteria("Antalya");

        List<HotelProduct> results = client.searchHotels(criteria);

        assertThat(results).hasSize(6);

        for (HotelProduct product : results) {
            assertThat(product.id()).isNotBlank();
            assertThat(product.hotelName()).isNotBlank();
            assertThat(product.region()).isNotBlank();
            assertThat(product.stars()).isBetween(1, 5);
            assertThat(product.price()).isNotNull();
            assertThat(product.currency()).isNotBlank();
            assertThat(product.boardType()).isNotBlank();
        }
    }

    @Test
    void authenticate_ReturnsMockToken() {
        MockTourVisioHotelApiClient client = new MockTourVisioHotelApiClient();
        String token = client.authenticate();
        assertThat(token).isEqualTo("mock-token");
    }

    @Test
    void getArrivalAutocomplete_ReturnsMockCity() {
        MockTourVisioHotelApiClient client = new MockTourVisioHotelApiClient();
        AutocompleteResponse response = client.getArrivalAutocomplete("Antalya");
        
        assertThat(response.header().success()).isTrue();
        assertThat(response.body().items()).hasSize(1);
        assertThat(response.body().items().get(0).city().name()).isEqualTo("Antalya");
    }

    @Test
    void priceSearch_ReturnsMockResultsFiltered() {
        MockTourVisioHotelApiClient client = new MockTourVisioHotelApiClient();
        HotelSearchRequest criteria = new HotelSearchRequest("Antalya", "2024-01-01", 5, 2, List.of(), "TR", "TRY", "tr-TR");
        
        Object result = client.priceSearch(criteria, "123");
        
        assertThat(result).isInstanceOf(List.class);
        assertThat((List<?>) result).isNotEmpty();
    }
}
