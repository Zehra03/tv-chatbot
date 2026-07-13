package com.paximum.paxassist.hotel;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Unit tests for the raw-TourVisio → {@link HotelProduct} mapping. This is the step that was
 * missing before: {@code priceSearch} returned the raw response, so {@code HotelController}
 * dropped every hotel to an empty list. These tests pin the mapping so it keeps producing
 * typed cards.
 */
class TourVisioHotelApiClientImplTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final TourVisioHotelApiClientImpl client = new TourVisioHotelApiClientImpl(objectMapper);

    @Test
    void mapToHotelProducts_mapsBodyHotelsToCards() throws Exception {
        String raw = """
                {
                  "body": {
                    "hotels": [
                      {
                        "id": "4481",
                        "name": "FATIH TEST",
                        "stars": 5,
                        "city": { "name": "Antalya" },
                        "offers": [
                          {
                            "offerId": "test-offer-123",
                            "isAvailable": true,
                            "price": { "amount": 42.00, "currency": "EUR" },
                            "rooms": [ { "boardName": "ALL INCLUSIVE" } ]
                          }
                        ]
                      }
                    ]
                  }
                }
                """;
        Object body = objectMapper.readValue(raw, Object.class);

        List<HotelProduct> products = client.mapToHotelProducts(body);

        assertThat(products).hasSize(1);
        HotelProduct p = products.get(0);
        assertThat(p.id()).isEqualTo("4481");
        assertThat(p.hotelName()).isEqualTo("FATIH TEST");
        assertThat(p.region()).isEqualTo("Antalya");
        assertThat(p.stars()).isEqualTo(5);
        assertThat(p.price()).isEqualByComparingTo(new BigDecimal("42.00"));
        assertThat(p.currency()).isEqualTo("EUR");
        assertThat(p.boardType()).isEqualTo("ALL INCLUSIVE");
        assertThat(p.availability()).isTrue();
        assertThat(p.image()).isNull(); // no thumbnailFull in this fixture
        assertThat(p.offerId()).isEqualTo("test-offer-123");
    }

    @Test
    void mapToHotelProducts_collectsFacilityAndThemeNamesAsFeatures() throws Exception {
        String raw = """
                {
                  "body": {
                    "hotels": [
                      {
                        "id": "5001", "name": "SEA HOTEL", "stars": 5, "city": { "name": "Antalya" },
                        "facilities": [
                          { "id": "8", "name": "Beach Hotel" },
                          { "id": "48", "name": "Private Beach" },
                          { "id": "8", "name": "Beach Hotel" }
                        ],
                        "themes": [ { "id": "32", "name": "Deniz Kenarında" }, { "id": "", "name": "" } ],
                        "offers": [ { "isAvailable": true, "price": { "amount": 100, "currency": "TRY" } } ]
                      }
                    ]
                  }
                }
                """;
        Object body = objectMapper.readValue(raw, Object.class);

        List<HotelProduct> products = client.mapToHotelProducts(body);

        assertThat(products).hasSize(1);
        // Distinct, order-preserving, blanks skipped; facilities then themes.
        assertThat(products.get(0).features())
                .containsExactly("Beach Hotel", "Private Beach", "Deniz Kenarında");
    }

    @Test
    void mapToHotelProducts_noFacilitiesOrThemes_yieldsEmptyFeatures() throws Exception {
        String raw = """
                { "body": { "hotels": [
                  { "id": "1", "name": "BARE HOTEL", "stars": 3, "city": { "name": "Izmir" } }
                ] } }
                """;
        Object body = objectMapper.readValue(raw, Object.class);

        assertThat(client.mapToHotelProducts(body).get(0).features()).isEmpty();
    }

    @Test
    void mapToHotelProducts_mapsThumbnailFullToImage() throws Exception {
        String raw = """
                {
                  "body": {
                    "hotels": [
                      {
                        "id": "9302",
                        "name": "SALE DENEME",
                        "stars": 5,
                        "city": { "name": "Antalya" },
                        "thumbnail": "/images/product/1/362/9302/sale_deneme.jpg",
                        "thumbnailFull": "https://test-service.tourvisio.com/media/images/product/1/362/9302/sale_deneme.jpg",
                        "offers": [ { "isAvailable": true, "price": { "amount": 42.00, "currency": "EUR" } } ]
                      }
                    ]
                  }
                }
                """;
        Object body = objectMapper.readValue(raw, Object.class);

        List<HotelProduct> products = client.mapToHotelProducts(body);

        assertThat(products).hasSize(1);
        assertThat(products.get(0).image())
                .isEqualTo("https://test-service.tourvisio.com/media/images/product/1/362/9302/sale_deneme.jpg");
    }

    @Test
    void mapToHotelProducts_blankThumbnailFull_mapsToNullImage() throws Exception {
        String raw = """
                { "body": { "hotels": [
                  { "id": "1", "name": "NO IMAGE", "stars": 3, "city": { "name": "Izmir" }, "thumbnailFull": "" }
                ] } }
                """;
        Object body = objectMapper.readValue(raw, Object.class);

        assertThat(client.mapToHotelProducts(body).get(0).image()).isNull();
    }

    @Test
    void mapToHotelProducts_emptyHotels_returnsEmptyList() throws Exception {
        Object body = objectMapper.readValue("{\"body\":{\"hotels\":[]}}", Object.class);

        assertThat(client.mapToHotelProducts(body)).isEmpty();
    }

    @Test
    void mapToHotelProducts_missingHotelsNode_returnsEmptyList() throws Exception {
        Object body = objectMapper.readValue("{\"body\":{}}", Object.class);

        assertThat(client.mapToHotelProducts(body)).isEmpty();
    }

    @Test
    void mapToHotelProducts_hotelWithoutOffers_usesSafeDefaults() throws Exception {
        String raw = """
                { "body": { "hotels": [
                  { "id": "1", "name": "NO OFFERS", "stars": 3, "city": { "name": "Izmir" } }
                ] } }
                """;
        Object body = objectMapper.readValue(raw, Object.class);

        List<HotelProduct> products = client.mapToHotelProducts(body);

        assertThat(products).hasSize(1);
        HotelProduct p = products.get(0);
        assertThat(p.id()).isEqualTo("1");
        assertThat(p.hotelName()).isEqualTo("NO OFFERS");
        assertThat(p.region()).isEqualTo("Izmir");
        assertThat(p.price()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(p.currency()).isEqualTo("TRY");
        assertThat(p.boardType()).isEqualTo("Unknown");
        assertThat(p.availability()).isFalse();
    }
}
