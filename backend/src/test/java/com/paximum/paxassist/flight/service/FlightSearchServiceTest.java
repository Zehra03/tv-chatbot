package com.paximum.paxassist.flight.service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
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

import com.paximum.paxassist.flight.config.TourVisioProperties;
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

    /** Only the timezone matters here — it is the zone the departure-time window is read in. */
    private static final TourVisioProperties PROPERTIES =
            new TourVisioProperties(null, null, "Europe/Istanbul", null, null, null);

    private FlightSearchService service() {
        return new TourVisioFlightSearchService(tourVisioFlightClient, requestMapper, responseMapper,
                eventPublisher, tokenProvider, locationResolver, PROPERTIES);
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
        when(responseMapper.toFlightProducts(response, criteria)).thenReturn(mappedProducts);

        FlightSearchOutcome outcome = service().search(criteria);

        assertThat(outcome.complete()).isTrue();
        assertThat(outcome.results()).isEqualTo(mappedProducts);
        verify(eventPublisher).publishEvent(any(FlightSearchEvent.class));
    }

    /**
     * TourVisio's price search takes none of nonstop / airline / departure window as a request
     * parameter, so the service must narrow the mapped results itself — before this, the flags reached
     * the criteria and were silently swallowed.
     */
    private FlightSearchOutcome searchWithMappedResults(FlightSearchCriteria criteria,
                                                        List<FlightProduct> mapped) {
        TourVisioPriceSearchRequest request = mock(TourVisioPriceSearchRequest.class);
        TourVisioPriceSearchResponse response = new TourVisioPriceSearchResponse(
                new TourVisioResponseHeader(true), new TourVisioResponseBody("search-1", List.of()));

        resolveLocationsIdentity();
        when(requestMapper.toRequest(any())).thenReturn(request);
        when(tourVisioFlightClient.priceSearch(request)).thenReturn(response);
        when(responseMapper.toFlightProducts(response, criteria)).thenReturn(mapped);

        return service().search(criteria);
    }

    /** Departure at the given Istanbul wall-clock hour — the zone the window is read in (PROPERTIES). */
    private FlightProduct flightDepartingAt(String id, String airline, int stops, int hour) {
        Instant depart = LocalDate.of(2026, 8, 10)
                .atTime(hour, 0)
                .atZone(ZoneId.of("Europe/Istanbul"))
                .toInstant();
        return FlightProduct.builder().id(id).airline(airline).stops(stops).departTime(depart).build();
    }

    @Test
    void search_departTimeWindow_narrowsResultsToTheRequestedHours() {
        List<FlightProduct> mapped = List.of(
                flightDepartingAt("early", "TK", 0, 6),
                flightDepartingAt("morning", "TK", 0, 9),
                flightDepartingAt("evening", "TK", 0, 20));
        FlightSearchCriteria criteria = completeCriteria().toBuilder()
                .departTimeFrom(LocalTime.of(8, 0))
                .departTimeTo(LocalTime.of(12, 0))
                .build();

        FlightSearchOutcome outcome = searchWithMappedResults(criteria, mapped);

        assertThat(outcome.results()).extracting(FlightProduct::getId).containsExactly("morning");
    }

    @Test
    void search_nonstopAndAirline_narrowResults() {
        List<FlightProduct> mapped = List.of(
                flightDepartingAt("tk-direct", "TK", 0, 9),
                flightDepartingAt("tk-connecting", "TK", 1, 9),
                flightDepartingAt("pc-direct", "PC", 0, 9));
        FlightSearchCriteria criteria = completeCriteria().toBuilder()
                .nonstop(true)
                .preferredAirline("TK")
                .build();

        FlightSearchOutcome outcome = searchWithMappedResults(criteria, mapped);

        assertThat(outcome.results()).extracting(FlightProduct::getId).containsExactly("tk-direct");
    }

    @Test
    void search_windowIsReadInTheConfiguredTourVisioZoneNotUtc() {
        // 09:00 Istanbul is 06:00 UTC. A UTC reading would place it outside an 08:00-12:00 window and
        // wrongly drop the flight — this is the regression the zone plumbing exists to prevent.
        List<FlightProduct> mapped = List.of(flightDepartingAt("morning", "TK", 0, 9));
        FlightSearchCriteria criteria = completeCriteria().toBuilder()
                .departTimeFrom(LocalTime.of(8, 0))
                .departTimeTo(LocalTime.of(12, 0))
                .build();

        FlightSearchOutcome outcome = searchWithMappedResults(criteria, mapped);

        assertThat(outcome.results()).extracting(FlightProduct::getId).containsExactly("morning");
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
        when(responseMapper.toFlightProducts(response, criteria)).thenReturn(List.of());

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
