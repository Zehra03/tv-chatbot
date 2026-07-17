package com.paximum.paxassist.hotel.facility;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.paximum.paxassist.hotel.dto.HotelFeatureDetails;

/**
 * Unit tests for {@link HotelFeatureMapper}. Feeds raw JSON payloads (both the hotel-level
 * GetProductInfo shape and edge cases) and asserts the standard feature model. No Spring / services.
 */
class HotelFeatureMapperTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private HotelFeatureMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new HotelFeatureMapper(new FacilityMappingService(objectMapper), objectMapper);
    }

    private JsonNode json(String raw) {
        try {
            return objectMapper.readTree(raw);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void mapsGetProductInfoShape() {
        // Mirrors the real payload: hotel-level facilities under seasons[].facilityCategories[],
        // themes[], and a room boardName.
        HotelFeatureDetails d = mapper.map(json("""
            {"body":{"hotel":{
              "themes":[{"id":"1","name":"FAMILY"},{"id":"2","name":"All Inclusive"},{"id":"3","name":"Spa & Relax"}],
              "seasons":[{"facilityCategories":[{"facilities":[
                {"id":"46","name":"Pets Allowed"},
                {"id":"47","name":"Pool"},
                {"id":"53","name":"Sauna"}
              ]}]}],
              "offers":[{"rooms":[{"boardName":"ALL INCLUSIVE"}]}]
            }}}
            """));

        assertTrue(d.hotelFeatures().petFriendly());
        assertTrue(d.hotelFeatures().otherFacilities().contains("pool"));
        assertTrue(d.hotelFeatures().otherFacilities().contains("spa_wellness"));
        assertFalse(d.hotelFeatures().otherFacilities().contains("pet_friendly"),
                "pet_friendly is surfaced via the boolean, not in otherFacilities");
        assertEquals(java.util.List.of("ALL_INCLUSIVE"), d.boardOptions());
        // "Spa & Relax" → "SPA_RELAX" (non-alphanumerics collapsed).
        assertEquals(java.util.List.of("FAMILY", "ALL_INCLUSIVE", "SPA_RELAX"), d.themeFilters());
    }

    @Test
    void facilitiesByNameOnlyStillGroup() {
        // Room-level facilities sometimes carry only a name, no id.
        HotelFeatureDetails d = mapper.map(json("""
            {"rooms":[{"facilities":[{"name":"Outdoor Pool"},{"name":"Private Beach"}]}]}
            """));
        assertTrue(d.hotelFeatures().otherFacilities().contains("pool"));
        assertTrue(d.hotelFeatures().otherFacilities().contains("beach"));
        assertFalse(d.hotelFeatures().petFriendly());
    }

    @Test
    void emptyThemesYieldEmptyThemeFiltersNotError() {
        HotelFeatureDetails d = mapper.map(json("""
            {"body":{"hotel":{"themes":[],"seasons":[]}}}
            """));
        assertEquals(java.util.List.of(), d.themeFilters());
        assertEquals(java.util.List.of(), d.boardOptions());
        assertFalse(d.hotelFeatures().petFriendly());
        assertEquals(java.util.List.of(), d.hotelFeatures().otherFacilities());
    }

    @Test
    void emptyPayloadIsSafe() {
        HotelFeatureDetails d = mapper.map(json("{}"));
        assertFalse(d.hotelFeatures().petFriendly());
        assertEquals(java.util.List.of(), d.hotelFeatures().otherFacilities());
        assertEquals(java.util.List.of(), d.boardOptions());
        assertEquals(java.util.List.of(), d.themeFilters());
    }
}
