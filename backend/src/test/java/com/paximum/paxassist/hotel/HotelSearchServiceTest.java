package com.paximum.paxassist.hotel;

import com.paximum.paxassist.common.log.LogModuleClient;
import com.paximum.paxassist.hotel.dto.AutocompleteResponse;
import com.paximum.paxassist.hotel.dto.HotelSearchRequest;
import com.paximum.paxassist.hotel.dto.HotelSearchResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

class HotelSearchServiceTest {

    private TourVisioHotelApiClient apiClient;
    private LogModuleClient logModuleClient;
    private HotelSearchService searchService;

    @BeforeEach
    void setUp() {
        apiClient = Mockito.mock(TourVisioHotelApiClient.class);
        logModuleClient = Mockito.mock(LogModuleClient.class);
        searchService = new HotelSearchServiceImpl(apiClient, logModuleClient);
    }

    @Test
    void searchHotels_WhenParametersMissing_ShouldReturnIncomplete() {
        HotelSearchRequest request = new HotelSearchRequest(
            "Antalya", null, null, null, List.of(), "TR", "TRY", "tr-TR"
        );

        HotelSearchResponse response = searchService.searchHotels(request);

        assertThat(response.status()).isEqualTo("INCOMPLETE");
        assertThat(response.missingParameters()).containsExactlyInAnyOrder("checkIn", "night", "adult");
        assertThat(response.results()).isNull();
    }

    @Test
    void searchHotels_WhenAllParametersPresent_ShouldCallApiAndReturnSuccess() {
        HotelSearchRequest request = new HotelSearchRequest(
            "Antalya", "2023-06-20", 7, 2, List.of(), "TR", "TRY", "tr-TR"
        );

        AutocompleteResponse autocompleteRes = new AutocompleteResponse(
            new AutocompleteResponse.Header(true),
            new AutocompleteResponse.Body(List.of(
                new AutocompleteResponse.Item(
                    1,
                    new AutocompleteResponse.City("23494", "Antalya"),
                    null
                )
            ))
        );
        when(apiClient.getArrivalAutocomplete("Antalya")).thenReturn(autocompleteRes);

        Map<String, Object> mockPriceResult = new HashMap<>();
        mockPriceResult.put("searchId", "12345");
        when(apiClient.priceSearch(eq(request), eq("23494"))).thenReturn(mockPriceResult);

        HotelSearchResponse response = searchService.searchHotels(request);

        assertThat(response.status()).isEqualTo("SUCCESS");
        assertThat(response.missingParameters()).isEmpty();
        assertThat(response.results()).isEqualTo(mockPriceResult);
    }
}
