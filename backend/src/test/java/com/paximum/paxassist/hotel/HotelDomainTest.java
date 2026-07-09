package com.paximum.paxassist.hotel;

import com.paximum.paxassist.hotel.dto.HotelSearchRequest;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

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
        String region = "Antalya";
        int stars = 5;
        BigDecimal price = new BigDecimal("1500.00");
        String currency = "EUR";
        String boardType = "All Inclusive";
        boolean availability = true;

        // When
        HotelProduct product = new HotelProduct(id, name, region, stars, price, currency, boardType, availability);

        // Then
        assertThat(product.id()).isEqualTo(id);
        assertThat(product.hotelName()).isEqualTo(name);
        assertThat(product.region()).isEqualTo(region);
        assertThat(product.stars()).isEqualTo(stars);
        assertThat(product.price()).isEqualTo(price);
        assertThat(product.currency()).isEqualTo(currency);
        assertThat(product.boardType()).isEqualTo(boardType);
        assertThat(product.availability()).isTrue();
    }

    @Test
    void shouldCreateHotelSearchRequestWithAllFields() {
        // Given
        String destination = "Antalya";
        String checkIn = "2023-08-01";
        Integer night = 7;
        Integer adult = 2;
        List<Integer> childAges = List.of(5, 8);
        String nationality = "TR";
        String currency = "EUR";
        String culture = "tr-TR";

        // When
        HotelSearchRequest request = new HotelSearchRequest(destination, checkIn, night, adult, childAges, nationality, currency, culture);

        // Then
        assertThat(request.destination()).isEqualTo(destination);
        assertThat(request.checkIn()).isEqualTo(checkIn);
        assertThat(request.night()).isEqualTo(night);
        assertThat(request.adult()).isEqualTo(adult);
        assertThat(request.childAges()).containsExactly(5, 8);
        assertThat(request.nationality()).isEqualTo(nationality);
        assertThat(request.currency()).isEqualTo(currency);
        assertThat(request.culture()).isEqualTo(culture);
    }
}
