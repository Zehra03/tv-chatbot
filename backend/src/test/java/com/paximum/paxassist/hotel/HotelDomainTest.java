package com.paximum.paxassist.hotel;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HotelDomainTest {

    @Test
    void shouldCreateHotelSearchCriteriaWithValidDestination() {
        // Given
        String destination = "Antalya";

        // When
        HotelSearchCriteria criteria = new HotelSearchCriteria(destination);

        // Then
        assertThat(criteria.destination()).isEqualTo(destination);
    }

    @Test
    void shouldCreateHotelProductWithAllFields() {
        // Given
        String id = "hotel-123";
        String name = "Rixos Premium Belek";
        String destination = "Antalya";

        // When
        HotelProduct product = new HotelProduct(id, name, destination);

        // Then
        assertThat(product.id()).isEqualTo(id);
        assertThat(product.hotelName()).isEqualTo(name);
        assertThat(product.destination()).isEqualTo(destination);
    }
}
