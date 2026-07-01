package com.paximum.paxassist.hotel;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class HotelDomainTest {

    @Test
    void shouldCreateHotelProductSuccessfully() {
        HotelProduct product = new HotelProduct(
                "H100",
                "Test Resort",
                "Antalya",
                5,
                new BigDecimal("150.50"),
                "EUR",
                "All Inclusive",
                true
        );

        assertThat(product.id()).isEqualTo("H100");
        assertThat(product.hotelName()).isEqualTo("Test Resort");
        assertThat(product.stars()).isEqualTo(5);
        assertThat(product.price()).isEqualTo(new BigDecimal("150.50"));
        assertThat(product.availability()).isTrue();
    }

    @Test
    void shouldCreateHotelSearchCriteriaSuccessfully() {
        HotelSearchCriteria criteria = new HotelSearchCriteria("Istanbul");

        assertThat(criteria.destination()).isEqualTo("Istanbul");
    }
}
