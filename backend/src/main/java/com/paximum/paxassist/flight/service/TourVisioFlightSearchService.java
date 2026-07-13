package com.paximum.paxassist.flight.service;

import java.util.List;
import java.util.Optional;

import feign.FeignException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import com.paximum.paxassist.flight.domain.FlightProduct;
import com.paximum.paxassist.flight.domain.FlightSearchCriteria;
import com.paximum.paxassist.flight.event.FlightSearchEvent;
import com.paximum.paxassist.flight.infrastructure.client.TourVisioFlightClient;
import com.paximum.paxassist.flight.infrastructure.client.TourVisioLocationResolver;
import com.paximum.paxassist.flight.infrastructure.client.TourVisioSearchException;
import com.paximum.paxassist.flight.infrastructure.client.TourVisioTokenProvider;
import com.paximum.paxassist.flight.infrastructure.dto.request.TourVisioPriceSearchRequest;
import com.paximum.paxassist.flight.infrastructure.dto.response.TourVisioPriceSearchResponse;
import com.paximum.paxassist.flight.infrastructure.mapper.TourVisioFlightRequestMapper;
import com.paximum.paxassist.flight.infrastructure.mapper.TourVisioFlightResponseMapper;

@Service
@Profile("!mock & !demo")
public class TourVisioFlightSearchService implements FlightSearchService {

    private static final Logger log = LoggerFactory.getLogger(TourVisioFlightSearchService.class);

    private final TourVisioFlightClient tourVisioFlightClient;
    private final TourVisioFlightRequestMapper requestMapper;
    private final TourVisioFlightResponseMapper responseMapper;
    private final ApplicationEventPublisher eventPublisher;
    private final TourVisioTokenProvider tokenProvider;
    private final TourVisioLocationResolver locationResolver;

    public TourVisioFlightSearchService(
            TourVisioFlightClient tourVisioFlightClient,
            TourVisioFlightRequestMapper requestMapper,
            TourVisioFlightResponseMapper responseMapper,
            ApplicationEventPublisher eventPublisher,
            TourVisioTokenProvider tokenProvider,
            TourVisioLocationResolver locationResolver) {
        this.tourVisioFlightClient = tourVisioFlightClient;
        this.requestMapper = requestMapper;
        this.responseMapper = responseMapper;
        this.eventPublisher = eventPublisher;
        this.tokenProvider = tokenProvider;
        this.locationResolver = locationResolver;
    }

    @Override
    @Cacheable(value = "flightSearch", key = "#criteria.toCacheKey()")
    public FlightSearchOutcome search(FlightSearchCriteria criteria) {
        List<String> missingFields = criteria.missingRequiredFields();
        if (!missingFields.isEmpty()) {
            log.info("Flight search incomplete: missingFields={}", missingFields);
            return FlightSearchOutcome.incomplete(missingFields);
        }

        // TourVisio price search matches locations by id ("AYT"), not the free text the user types
        // ("Antalya"). Resolve both ends via autocomplete first; an unresolvable place is treated as
        // "no flights" (empty, HTTP 200) rather than a TourVisio failure (502).
        Optional<String> originId = locationResolver.resolveDeparture(criteria.getOrigin());
        Optional<String> destinationId = locationResolver.resolveArrival(criteria.getDestination());
        if (originId.isEmpty() || destinationId.isEmpty()) {
            log.info("Flight search could not resolve locations: origin='{}'->{}, destination='{}'->{}",
                    criteria.getOrigin(), originId.orElse("<none>"),
                    criteria.getDestination(), destinationId.orElse("<none>"));
            return FlightSearchOutcome.complete(List.of());
        }

        FlightSearchCriteria resolvedCriteria = criteria.toBuilder()
                .origin(originId.get())
                .destination(destinationId.get())
                .build();

        log.info("Starting flight search: origin={} (from '{}'), destination={} (from '{}'), tripType={}",
                originId.get(), criteria.getOrigin(), destinationId.get(), criteria.getDestination(),
                criteria.getTripType());

        TourVisioPriceSearchRequest request = requestMapper.toRequest(resolvedCriteria);
        TourVisioPriceSearchResponse response;
        try {
            response = tourVisioFlightClient.priceSearch(request);
        } catch (FeignException.Unauthorized e) {
            log.warn("TourVisio rejected the cached token; re-logging in and retrying once");
            tokenProvider.invalidate();
            response = tourVisioFlightClient.priceSearch(request);
        }

        if (response == null || response.header() == null || !response.header().success()) {
            eventPublisher.publishEvent(FlightSearchEvent.failure(criteria));
            throw new TourVisioSearchException(
                    "TourVisio price search returned no success result: origin=%s, destination=%s"
                            .formatted(criteria.getOrigin(), criteria.getDestination()));
        }

        List<FlightProduct> products = FlightResultFilter.apply(
                criteria, responseMapper.toFlightProducts(response, criteria.getTripType()));
        eventPublisher.publishEvent(FlightSearchEvent.success(criteria, products.size()));

        log.info("Flight search completed: origin={}, destination={}, resultCount={}",
                criteria.getOrigin(), criteria.getDestination(), products.size());

        return FlightSearchOutcome.complete(products);
    }
}
