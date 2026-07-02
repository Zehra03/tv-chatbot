package com.paximum.paxassist.hotel;

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
            assertThat(product.availability()).isTrue();
        }
    }
}
