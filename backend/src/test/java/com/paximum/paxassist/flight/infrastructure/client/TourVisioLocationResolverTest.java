package com.paximum.paxassist.flight.infrastructure.client;

import java.util.List;
import java.util.Map;

import feign.FeignException;
import feign.Request;
import feign.RequestTemplate;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.paximum.paxassist.flight.config.TourVisioProperties;
import com.paximum.paxassist.flight.infrastructure.dto.response.TourVisioAutocompleteResponse;
import com.paximum.paxassist.flight.infrastructure.dto.response.TourVisioAutocompleteResponse.Airport;
import com.paximum.paxassist.flight.infrastructure.dto.response.TourVisioAutocompleteResponse.City;
import com.paximum.paxassist.flight.infrastructure.dto.response.TourVisioAutocompleteResponse.Item;
import com.paximum.paxassist.flight.infrastructure.dto.response.TourVisioResponseHeader;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TourVisioLocationResolverTest {

    @Mock
    private TourVisioFlightClient flightClient;
    @Mock
    private TourVisioTokenProvider tokenProvider;

    private final TourVisioProperties properties =
            new TourVisioProperties("https://test.example.com", "tr-TR", "Europe/Istanbul", "a", "u", "p");

    private TourVisioLocationResolver resolver() {
        return new TourVisioLocationResolver(flightClient, properties, tokenProvider);
    }

    private static TourVisioAutocompleteResponse response(Item... items) {
        return new TourVisioAutocompleteResponse(
                new TourVisioResponseHeader(true),
                new TourVisioAutocompleteResponse.Body(List.of(items)));
    }

    private static Item airport(String code) {
        return new Item(3, null, new Airport(code, code, code + " Airport"));
    }

    private static Item city(String id) {
        return new Item(1, new City(id, id + " (all airports)"), null);
    }

    @Test
    void resolveDeparture_prefersExactCodeMatchOverTopSuggestion() {
        // TourVisio ranks Puerto Ayacucho (PYH) above Antalya (AYT) for the query "AYT".
        when(flightClient.departureAutocomplete(any())).thenReturn(response(airport("PYH"), airport("AYT")));

        assertThat(resolver().resolveDeparture("AYT")).contains("AYT");
    }

    @Test
    void resolveDeparture_fallsBackToTopSuggestionForPlaceName() {
        when(flightClient.departureAutocomplete(any())).thenReturn(response(airport("AYT")));

        assertThat(resolver().resolveDeparture("Antalya")).contains("AYT");
    }

    @Test
    void resolveArrival_picksCityIdWhenFirstItemIsCity() {
        when(flightClient.arrivalAutocomplete(any()))
                .thenReturn(response(city("IST"), airport("IST"), airport("SAW")));

        assertThat(resolver().resolveArrival("Istanbul")).contains("IST");
    }

    @Test
    void resolve_returnsEmptyWhenNoSuggestions() {
        when(flightClient.departureAutocomplete(any())).thenReturn(response());

        assertThat(resolver().resolveDeparture("steinfrurt")).isEmpty();
    }

    @Test
    void resolve_returnsEmptyForBlankInputWithoutCallingTourVisio() {
        assertThat(resolver().resolveDeparture("   ")).isEmpty();
        verifyNoInteractions(flightClient);
    }

    @Test
    void resolve_retriesOnceOnUnauthorizedThenSucceeds() {
        Request feignRequest = Request.create(Request.HttpMethod.POST, "/productservice/getarrivalautocomplete",
                Map.of(), null, new RequestTemplate());
        FeignException.Unauthorized unauthorized =
                new FeignException.Unauthorized("unauthorized", feignRequest, null, null);
        when(flightClient.arrivalAutocomplete(any()))
                .thenThrow(unauthorized)
                .thenReturn(response(city("IST")));

        assertThat(resolver().resolveArrival("Istanbul")).contains("IST");

        verify(tokenProvider).invalidate();
        verify(flightClient, times(2)).arrivalAutocomplete(any());
    }
}
