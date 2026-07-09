package com.paximum.paxassist.flight.service;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import feign.FeignException;
import feign.Request;
import feign.RequestTemplate;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.context.ApplicationEventPublisher;

import com.paximum.paxassist.flight.domain.FlightProduct;
import com.paximum.paxassist.flight.domain.FlightSearchCriteria;
import com.paximum.paxassist.flight.domain.PassengerCount;
import com.paximum.paxassist.flight.domain.TripType;
import com.paximum.paxassist.flight.event.FlightSearchEvent;
import com.paximum.paxassist.flight.infrastructure.client.TourVisioFlightClient;
import com.paximum.paxassist.flight.infrastructure.client.TourVisioLocationResolver;
import com.paximum.paxassist.flight.infrastructure.client.TourVisioSearchException;
import com.paximum.paxassist.flight.infrastructure.client.TourVisioTokenProvider;
import com.paximum.paxassist.flight.infrastructure.dto.request.TourVisioPriceSearchRequest;
import com.paximum.paxassist.flight.infrastructure.dto.response.TourVisioPriceSearchResponse;
import com.paximum.paxassist.flight.infrastructure.dto.response.TourVisioResponseBody;
import com.paximum.paxassist.flight.infrastructure.dto.response.TourVisioResponseHeader;
import com.paximum.paxassist.flight.infrastructure.mapper.TourVisioFlightRequestMapper;
import com.paximum.paxassist.flight.infrastructure.mapper.TourVisioFlightResponseMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FlightSearchServiceTest {

    @Mock
    private TourVisioFlightClient tourVisioFlightClient;
    @Mock
    private TourVisioFlightRequestMapper requestMapper;
    @Mock
    private TourVisioFlightResponseMapper responseMapper;
    @Mock
    private ApplicationEventPublisher eventPublisher;
    @Mock
    private TourVisioTokenProvider tokenProvider;
    @Mock
    private TourVisioLocationResolver locationResolver;

    private FlightSearchService service() {
        return new TourVisioFlightSearchService(
                tourVisioFlightClient, requestMapper, responseMapper, eventPublisher, tokenProvider, locationResolver);
    }

    /** Stubs autocomplete to resolve the completeCriteria() origin/destination to themselves. */
    private void resolveLocationsIdentity() {
        when(locationResolver.resolveDeparture("IST")).thenReturn(Optional.of("IST"));
        when(locationResolver.resolveArrival("LHR")).thenReturn(Optional.of("LHR"));
    }

    private FlightSearchCriteria completeCriteria() {
        return FlightSearchCriteria.builder()
                .origin("IST")
                .destination("LHR")
                .departDate(LocalDate.of(2026, 8, 10))
                .tripType(TripType.ONE_WAY)
                .currency("USD")
                .passengers(PassengerCount.builder().adults(1).children(0).infants(0).build())
                .build();
    }

    @Test
    void search_returnsIncompleteOutcomeWithoutCallingTourVisio() {
        FlightSearchCriteria incompleteCriteria = FlightSearchCriteria.builder().build();

        FlightSearchOutcome outcome = service().search(incompleteCriteria);

        assertThat(outcome.complete()).isFalse();
        assertThat(outcome.missingFields()).isNotEmpty();
        verify(tourVisioFlightClient, never()).priceSearch(any());
    }

    @Test
    void search_returnsCompleteOutcomeOnHappyPath() {
        FlightSearchCriteria criteria = completeCriteria();
        TourVisioPriceSearchRequest request = mock(TourVisioPriceSearchRequest.class);
        TourVisioPriceSearchResponse response = new TourVisioPriceSearchResponse(
                new TourVisioResponseHeader(true), new TourVisioResponseBody("search-1", List.of()));
        List<FlightProduct> mappedProducts = List.of(FlightProduct.builder().id("p1").build());

        resolveLocationsIdentity();
        when(requestMapper.toRequest(any())).thenReturn(request);
        when(tourVisioFlightClient.priceSearch(request)).thenReturn(response);
        when(responseMapper.toFlightProducts(response, TripType.ONE_WAY)).thenReturn(mappedProducts);

        FlightSearchOutcome outcome = service().search(criteria);

        assertThat(outcome.complete()).isTrue();
        assertThat(outcome.results()).isEqualTo(mappedProducts);
        verify(eventPublisher).publishEvent(any(FlightSearchEvent.class));
    }

    @Test
    void search_returnsEmptyResultWhenLocationUnresolvable() {
        FlightSearchCriteria criteria = completeCriteria();
        when(locationResolver.resolveDeparture("IST")).thenReturn(Optional.of("IST"));
        when(locationResolver.resolveArrival("LHR")).thenReturn(Optional.empty());

        FlightSearchOutcome outcome = service().search(criteria);

        assertThat(outcome.complete()).isTrue();
        assertThat(outcome.results()).isEmpty();
        verify(tourVisioFlightClient, never()).priceSearch(any());
    }

    @Test
    void search_retriesOnceOnUnauthorizedThenSucceeds() {
        FlightSearchCriteria criteria = completeCriteria();
        TourVisioPriceSearchRequest request = mock(TourVisioPriceSearchRequest.class);
        TourVisioPriceSearchResponse response = new TourVisioPriceSearchResponse(
                new TourVisioResponseHeader(true), new TourVisioResponseBody("search-1", List.of()));
        Request feignRequest = Request.create(Request.HttpMethod.POST, "/productservice/pricesearch",
                java.util.Map.of(), null, new RequestTemplate());
        FeignException.Unauthorized unauthorized =
                new FeignException.Unauthorized("unauthorized", feignRequest, null, null);

        resolveLocationsIdentity();
        when(requestMapper.toRequest(any())).thenReturn(request);
        when(tourVisioFlightClient.priceSearch(request))
                .thenThrow(unauthorized)
                .thenReturn(response);
        when(responseMapper.toFlightProducts(response, TripType.ONE_WAY)).thenReturn(List.of());

        FlightSearchOutcome outcome = service().search(criteria);

        assertThat(outcome.complete()).isTrue();
        verify(tokenProvider).invalidate();
        verify(tourVisioFlightClient, times(2)).priceSearch(request);
    }

    @Test
    void search_throwsAndPublishesFailureWhenResponseUnsuccessful() {
        FlightSearchCriteria criteria = completeCriteria();
        TourVisioPriceSearchRequest request = mock(TourVisioPriceSearchRequest.class);
        TourVisioPriceSearchResponse response = new TourVisioPriceSearchResponse(
                new TourVisioResponseHeader(false), new TourVisioResponseBody("search-1", List.of()));

        resolveLocationsIdentity();
        when(requestMapper.toRequest(any())).thenReturn(request);
        when(tourVisioFlightClient.priceSearch(request)).thenReturn(response);

        assertThatThrownBy(() -> service().search(criteria))
                .isInstanceOf(TourVisioSearchException.class);

        verify(eventPublisher).publishEvent(any(FlightSearchEvent.class));
    }
}
