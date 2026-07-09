package com.paximum.paxassist.flight.infrastructure.mapper;

import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.paximum.paxassist.flight.config.TourVisioProperties;
import com.paximum.paxassist.flight.domain.FlightProduct;
import com.paximum.paxassist.flight.domain.TripType;
import com.paximum.paxassist.flight.infrastructure.dto.response.TourVisioBaggageInfo;
import com.paximum.paxassist.flight.infrastructure.dto.response.TourVisioFlightItem;
import com.paximum.paxassist.flight.infrastructure.dto.response.TourVisioFlightPoint;
import com.paximum.paxassist.flight.infrastructure.dto.response.TourVisioFlightResult;
import com.paximum.paxassist.flight.infrastructure.dto.response.TourVisioOffer;
import com.paximum.paxassist.flight.infrastructure.dto.response.TourVisioPriceSearchResponse;

@Component
public class TourVisioFlightResponseMapper {

    private static final Logger log = LoggerFactory.getLogger(TourVisioFlightResponseMapper.class);

    private final TourVisioProperties tourVisioProperties;

    public TourVisioFlightResponseMapper(TourVisioProperties tourVisioProperties) {
        this.tourVisioProperties = tourVisioProperties;
    }

    public List<FlightProduct> toFlightProducts(TourVisioPriceSearchResponse response, TripType requestedTripType) {
        if (response == null || response.body() == null || response.body().flights() == null) {
            return List.of();
        }

        List<FlightProduct> products = new ArrayList<>();
        for (TourVisioFlightResult flight : response.body().flights()) {
            try {
                toFlightProduct(flight, requestedTripType).ifPresent(products::add);
            } catch (DateTimeException e) {
                log.warn("Skipping flight result {} due to unparsable date/timezone data: {}",
                        flight.id(), e.getMessage());
            }
        }
        return products;
    }

    private Optional<FlightProduct> toFlightProduct(TourVisioFlightResult flight, TripType requestedTripType) {
        if (flight.items() == null || flight.items().isEmpty() || flight.offer() == null) {
            return Optional.empty();
        }

        TourVisioFlightItem item = flight.items().get(0);
        TourVisioOffer offer = flight.offer();

        if (item.departure() == null || item.departure().airport() == null
                || item.arrival() == null || item.arrival().airport() == null) {
            log.warn("Skipping flight result {} due to missing departure/arrival airport data", flight.id());
            return Optional.empty();
        }

        if (requestedTripType == TripType.ROUND_TRIP) {
            // TourVisio's pricesearch response gives no signal (in the schema seen so far) for
            // distinguishing a return leg from outbound connection segments, so round-trip
            // results are mapped as outbound-only until a live round-trip sample is available.
            log.warn("Flight result {} has no resolvable return leg for a round-trip search; "
                    + "mapping outbound data only", flight.id());
        }

        return Optional.of(FlightProduct.builder()
                .id(offer.offerId())
                .airline(item.airline() != null ? item.airline().internationalCode() : null)
                .flightNumber(FlightNumberParser.parse(item.flightNo()).number())
                .origin(item.departure().airport().id())
                .destination(item.arrival().airport().id())
                .originCity(cityNameOf(item.departure()))
                .destinationCity(cityNameOf(item.arrival()))
                .departTime(toInstant(item.departure().date()))
                .arriveTime(toInstant(item.arrival().date()))
                .returnDepartTime(null)
                .returnArriveTime(null)
                .stops(item.stopCount())
                .durationMinutes(item.duration())
                .baggage(summarizeBaggage(item.baggageInformations()))
                .price(offer.price() != null ? offer.price().amount() : null)
                .currency(offer.price() != null ? offer.price().currency() : null)
                .build());
    }

    /** City name the flight point sits in (e.g. airport SAW → "Istanbul"), null when absent. */
    private String cityNameOf(TourVisioFlightPoint point) {
        return point != null && point.city() != null ? point.city().name() : null;
    }

    private String summarizeBaggage(List<TourVisioBaggageInfo> baggageInformations) {
        if (baggageInformations == null || baggageInformations.isEmpty()) {
            return null;
        }
        return baggageInformations.stream()
                .map(b -> b.piece() + "x" + b.weight() + "kg")
                .collect(Collectors.joining(", "));
    }

    private Instant toInstant(String date) {
        if (date == null) {
            return null;
        }
        if (tourVisioProperties.timezone() == null || tourVisioProperties.timezone().isBlank()) {
            throw new DateTimeException("Missing tourvisio timezone");
        }
        LocalDateTime localDateTime = LocalDateTime.parse(date, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        ZoneId zone = ZoneId.of(tourVisioProperties.timezone());
        return localDateTime.atZone(zone).toInstant();
    }
}
