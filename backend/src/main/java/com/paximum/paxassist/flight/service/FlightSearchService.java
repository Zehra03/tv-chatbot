package com.paximum.paxassist.flight.service;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import com.paximum.paxassist.flight.domain.FlightProduct;
import com.paximum.paxassist.flight.domain.FlightSearchCriteria;
import com.paximum.paxassist.flight.event.FlightSearchEvent;
import com.paximum.paxassist.flight.infrastructure.client.TourVisioFlightClient;
import com.paximum.paxassist.flight.infrastructure.client.TourVisioSearchException;
import com.paximum.paxassist.flight.infrastructure.dto.request.TourVisioPriceSearchRequest;
import com.paximum.paxassist.flight.infrastructure.dto.response.TourVisioPriceSearchResponse;
import com.paximum.paxassist.flight.infrastructure.mapper.TourVisioFlightRequestMapper;
import com.paximum.paxassist.flight.infrastructure.mapper.TourVisioFlightResponseMapper;

@Service
public class FlightSearchService {

    private static final Logger log = LoggerFactory.getLogger(FlightSearchService.class);

    private final TourVisioFlightClient tourVisioFlightClient;
    private final TourVisioFlightRequestMapper requestMapper;
    private final TourVisioFlightResponseMapper responseMapper;
    private final ApplicationEventPublisher eventPublisher;

    public FlightSearchService(
            TourVisioFlightClient tourVisioFlightClient,
            TourVisioFlightRequestMapper requestMapper,
            TourVisioFlightResponseMapper responseMapper,
            ApplicationEventPublisher eventPublisher) {
        this.tourVisioFlightClient = tourVisioFlightClient;
        this.requestMapper = requestMapper;
        this.responseMapper = responseMapper;
        this.eventPublisher = eventPublisher;
    }

    @Cacheable(value = "flightSearch", key = "#criteria.toCacheKey()")
    public FlightSearchOutcome search(FlightSearchCriteria criteria) {
        List<String> missingFields = criteria.missingRequiredFields();
        if (!missingFields.isEmpty()) {
            log.info("Flight search incomplete: missingFields={}", missingFields);
            return FlightSearchOutcome.incomplete(missingFields);
        }

        log.info("Starting flight search: origin={}, destination={}, tripType={}",
                criteria.getOrigin(), criteria.getDestination(), criteria.getTripType());

        TourVisioPriceSearchRequest request = requestMapper.toRequest(criteria);
        TourVisioPriceSearchResponse response = tourVisioFlightClient.priceSearch(request);

        if (response == null || response.header() == null || !response.header().success()) {
            eventPublisher.publishEvent(FlightSearchEvent.failure(criteria));
            throw new TourVisioSearchException(
                    "TourVisio price search returned no success result: origin=%s, destination=%s"
                            .formatted(criteria.getOrigin(), criteria.getDestination()));
        }

        List<FlightProduct> products = responseMapper.toFlightProducts(response, criteria.getTripType());
        eventPublisher.publishEvent(FlightSearchEvent.success(criteria, products.size()));

        log.info("Flight search completed: origin={}, destination={}, resultCount={}",
                criteria.getOrigin(), criteria.getDestination(), products.size());

        return FlightSearchOutcome.complete(products);
    }
}
