package com.paximum.paxassist.hotel;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.paximum.paxassist.hotel.dto.AutocompleteResponse;
import com.paximum.paxassist.hotel.dto.HotelFeatureDetails;
import com.paximum.paxassist.hotel.dto.HotelSearchRequest;
import com.paximum.paxassist.hotel.facility.FacilityMappingService;
import com.paximum.paxassist.hotel.facility.HotelFeatureMapper;

/**
 * Tests the board-overlay rule in {@link HotelDetailsService}: GetProductInfo carries no board, so
 * the search card's {@code boardType} fills {@code boardOptions} — but only when the payload itself
 * had none. Uses a tiny stub client (no Spring/provider).
 */
class HotelDetailsServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    private HotelDetailsService serviceReturning(Object rawProductInfo) {
        HotelFeatureMapper mapper = new HotelFeatureMapper(new FacilityMappingService(objectMapper), objectMapper);
        TourVisioHotelApiClient stub = new TourVisioHotelApiClient() {
            @Override public Object getProductInfo(String productId, Integer ownerProvider) { return rawProductInfo; }
            @Override public List<HotelProduct> searchHotels(HotelSearchCriteria criteria) { return List.of(); }
            @Override public String authenticate() { return "t"; }
            @Override public AutocompleteResponse getArrivalAutocomplete(String query) { return null; }
            @Override public Object priceSearch(HotelSearchRequest criteria, String locationId) { return null; }
        };
        return new HotelDetailsService(stub, mapper);
    }

    @Test
    void boardTypeFillsBoardOptionsWhenPayloadHasNone() {
        // GetProductInfo shape without any boardName.
        Object raw = Map.of("body", Map.of("hotel", Map.of(
                "seasons", List.of(Map.of("facilityCategories", List.of(Map.of(
                        "facilities", List.of(Map.of("id", "47", "name", "Pool")))))))));
        HotelFeatureDetails d = serviceReturning(raw).getFeatureDetails("9302", 1, "ALL INCLUSIVE");
        assertEquals(List.of("ALL_INCLUSIVE"), d.boardOptions());
        assertEquals(List.of("pool"), d.hotelFeatures().otherFacilities());
    }

    @Test
    void payloadBoardWinsOverPassedBoardType() {
        // If the payload already carries board data, the passed card boardType must NOT override it.
        Object raw = Map.of("offers", List.of(Map.of("rooms", List.of(Map.of("boardName", "Half Board")))));
        HotelFeatureDetails d = serviceReturning(raw).getFeatureDetails("9302", 1, "ALL INCLUSIVE");
        assertEquals(List.of("HALF_BOARD"), d.boardOptions());
    }

    @Test
    void noBoardAnywhereYieldsEmptyBoardOptions() {
        Object raw = Map.of("body", Map.of("hotel", Map.of("themes", List.of())));
        HotelFeatureDetails d = serviceReturning(raw).getFeatureDetails("9302", 1, null);
        assertEquals(List.of(), d.boardOptions());
    }
}
