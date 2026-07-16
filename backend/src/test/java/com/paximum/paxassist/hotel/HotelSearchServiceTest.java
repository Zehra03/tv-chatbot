package com.paximum.paxassist.hotel;

import com.paximum.paxassist.common.log.LogModuleClient;
import com.paximum.paxassist.hotel.dto.AutocompleteResponse;
import com.paximum.paxassist.hotel.dto.HotelLocationDto;
import com.paximum.paxassist.hotel.dto.HotelSearchRequest;
import com.paximum.paxassist.hotel.dto.HotelSearchResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
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

    // ── suggestLocations ──────────────────────────────────────────────────────────────────────

    @Test
    void suggestLocations_mapsCityItemsAndDedupesById() {
        AutocompleteResponse response = new AutocompleteResponse(
                new AutocompleteResponse.Header(true),
                new AutocompleteResponse.Body(List.of(
                        new AutocompleteResponse.Item(1, new AutocompleteResponse.City("23494", "Antalya"), null),
                        // A hotel item under the same city — no name → not a place suggestion.
                        new AutocompleteResponse.Item(2, null, new AutocompleteResponse.Hotel("H1", "23494")),
                        // Duplicate city id → deduped.
                        new AutocompleteResponse.Item(1, new AutocompleteResponse.City("23494", "Antalya"), null),
                        new AutocompleteResponse.Item(1, new AutocompleteResponse.City("100", "Antakya"), null))));
        when(apiClient.getArrivalAutocomplete("Ant")).thenReturn(response);

        List<HotelLocationDto> locations = searchService.suggestLocations("Ant");

        assertThat(locations).extracting(HotelLocationDto::id).containsExactly("23494", "100");
        assertThat(locations).extracting(HotelLocationDto::name).containsExactly("Antalya", "Antakya");
        assertThat(locations).allSatisfy(l -> assertThat(l.type()).isEqualTo("city"));
    }

    @Test
    void suggestLocations_floatsPrefixMatchesAboveLooseContainsMatches() {
        AutocompleteResponse response = new AutocompleteResponse(
                new AutocompleteResponse.Header(true),
                new AutocompleteResponse.Body(List.of(
                        // TourVisio lists a loose substring match ("...anta...") ahead of the real prefix.
                        new AutocompleteResponse.Item(1, new AutocompleteResponse.City("1", "Costa de Cantabria"), null),
                        new AutocompleteResponse.Item(1, new AutocompleteResponse.City("2", "Antalya"), null))));
        when(apiClient.getArrivalAutocomplete("Anta")).thenReturn(response);

        List<HotelLocationDto> locations = searchService.suggestLocations("Anta");

        assertThat(locations).extracting(HotelLocationDto::name)
                .containsExactly("Antalya", "Costa de Cantabria");
    }

    @Test
    void suggestLocations_decodesHtmlEntitiesInNames() {
        AutocompleteResponse response = new AutocompleteResponse(
                new AutocompleteResponse.Header(true),
                new AutocompleteResponse.Body(List.of(
                        new AutocompleteResponse.Item(1, new AutocompleteResponse.City("1", "L&#39;Estartit"), null))));
        when(apiClient.getArrivalAutocomplete("Est")).thenReturn(response);

        assertThat(searchService.suggestLocations("Est"))
                .extracting(HotelLocationDto::name).containsExactly("L'Estartit");
    }

    @Test
    void suggestLocations_whenProviderFails_ReturnsEmpty() {
        when(apiClient.getArrivalAutocomplete("Ant")).thenThrow(new RuntimeException("boom"));

        assertThat(searchService.suggestLocations("Ant")).isEmpty();
    }

    @Test
    void suggestLocations_whenQueryBlank_ReturnsEmptyWithoutProviderCall() {
        assertThat(searchService.suggestLocations("  ")).isEmpty();
        verify(apiClient, never()).getArrivalAutocomplete(any());
    }

    // ── suggestAvailableCheckInDates ──────────────────────────────────────────────────────────

    private AutocompleteResponse autocompleteFor(String cityId, String cityName) {
        return new AutocompleteResponse(
                new AutocompleteResponse.Header(true),
                new AutocompleteResponse.Body(List.of(
                        new AutocompleteResponse.Item(1, new AutocompleteResponse.City(cityId, cityName), null))));
    }

    /** Anchor two months out so every probed candidate stays in the future regardless of wall clock. */
    private HotelSearchRequest baseSearch(LocalDate checkIn) {
        return new HotelSearchRequest(
                "Urla", checkIn.toString(), 6, 1, List.of(), "TR", "TRY", "tr-TR");
    }

    @Test
    void suggestDates_ReturnsOnlyProviderConfirmedDates_AndResolvesLocationOnce() {
        LocalDate anchor = LocalDate.now().plusMonths(2);
        HotelSearchRequest base = baseSearch(anchor);
        String availableDate = anchor.plusDays(4).toString(); // offset 4 (step 2 → 2,4,6,…)

        when(apiClient.getArrivalAutocomplete("Urla")).thenReturn(autocompleteFor("23494", "Urla"));
        when(apiClient.priceSearch(any(HotelSearchRequest.class), eq("23494")))
                .thenAnswer(invocation -> {
                    HotelSearchRequest probe = invocation.getArgument(0);
                    return availableDate.equals(probe.checkIn())
                            ? List.of(new HotelProduct("H1", "Rixos", "Urla", 5,
                                    new BigDecimal("1500"), "TRY", "AI", true))
                            : List.of();
                });

        List<String> dates = searchService.suggestAvailableCheckInDates(base, 2, 14, 3);

        assertThat(dates).containsExactly(availableDate);
        // Location resolved once and reused across every probe (no autocomplete per candidate).
        verify(apiClient, times(1)).getArrivalAutocomplete("Urla");
    }

    @Test
    void suggestDates_WhenLocationUnresolved_ReturnsEmptyAndNeverPriceSearches() {
        HotelSearchRequest base = baseSearch(LocalDate.now().plusMonths(2));
        when(apiClient.getArrivalAutocomplete("Urla")).thenReturn(
                new AutocompleteResponse(new AutocompleteResponse.Header(true),
                        new AutocompleteResponse.Body(List.of())));

        List<String> dates = searchService.suggestAvailableCheckInDates(base, 2, 14, 3);

        assertThat(dates).isEmpty();
        verify(apiClient, never()).priceSearch(any(), any());
    }

    @Test
    void suggestDates_WhenEssentialsMissing_ReturnsEmptyWithoutAnyProviderCall() {
        HotelSearchRequest base = new HotelSearchRequest(
                "Urla", LocalDate.now().plusMonths(2).toString(), null, null, List.of(), "TR", "TRY", "tr-TR");

        List<String> dates = searchService.suggestAvailableCheckInDates(base, 2, 14, 3);

        assertThat(dates).isEmpty();
        verify(apiClient, never()).getArrivalAutocomplete(any());
        verify(apiClient, never()).priceSearch(any(), any());
    }

    @Test
    void suggestDates_StopsAtMaxResults() {
        LocalDate anchor = LocalDate.now().plusMonths(2);
        HotelSearchRequest base = baseSearch(anchor);

        when(apiClient.getArrivalAutocomplete("Urla")).thenReturn(autocompleteFor("23494", "Urla"));
        // Every candidate is available → the probe must still cap at maxResults (2 here).
        when(apiClient.priceSearch(any(HotelSearchRequest.class), eq("23494")))
                .thenReturn(List.of(new HotelProduct("H1", "Rixos", "Urla", 5,
                        new BigDecimal("1500"), "TRY", "AI", true)));

        List<String> dates = searchService.suggestAvailableCheckInDates(base, 2, 14, 2);

        assertThat(dates).containsExactly(anchor.plusDays(2).toString(), anchor.plusDays(4).toString());
    }
}
