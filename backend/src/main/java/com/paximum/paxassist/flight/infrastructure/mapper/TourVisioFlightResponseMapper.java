package com.paximum.paxassist.flight.infrastructure.mapper;

import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAccessor;
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

    /** TourVisio marks each segment's leg with {@code route}: 1 = outbound, 2 = inbound/return. */
    private static final int ROUTE_OUTBOUND = 1;
    private static final int ROUTE_RETURN = 2;

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

        List<TourVisioFlightItem> outbound = legSegments(flight.items(), ROUTE_OUTBOUND);
        List<TourVisioFlightItem> inbound = legSegments(flight.items(), ROUTE_RETURN);

        if (outbound.isEmpty()) {
            log.warn("Skipping flight result {}: no outbound (route={}) segments", flight.id(), ROUTE_OUTBOUND);
            return Optional.empty();
        }

        // A leg with a layover arrives as several segments: the leg departs with the first and
        // lands with the last.
        TourVisioFlightItem first = outbound.get(0);
        TourVisioFlightItem last = outbound.get(outbound.size() - 1);
        TourVisioOffer offer = flight.offer();

        if (first.departure() == null || first.departure().airport() == null
                || last.arrival() == null || last.arrival().airport() == null) {
            log.warn("Skipping flight result {} due to missing departure/arrival airport data", flight.id());
            return Optional.empty();
        }

        if (requestedTripType == TripType.ROUND_TRIP && inbound.isEmpty()) {
            log.warn("Round-trip search returned flight result {} with no return (route={}) segments; "
                    + "mapping outbound data only", flight.id(), ROUTE_RETURN);
        }

        return Optional.of(FlightProduct.builder()
                .id(flight.id())
                .offerId(offer.offerId())
                .airline(first.airline() != null ? first.airline().internationalCode() : null)
                .flightNumber(FlightNumberParser.parse(first.flightNo()).number())
                .origin(first.departure().airport().id())
                .destination(last.arrival().airport().id())
                .originCity(cityNameOf(first.departure()))
                .destinationCity(cityNameOf(last.arrival()))
                .departTime(toInstant(first.departure().date()))
                .arriveTime(toInstant(last.arrival().date()))
                .returnDepartTime(legDepartTime(inbound))
                .returnArriveTime(legArriveTime(inbound))
                .stops(stopsOf(outbound))
                .durationMinutes(durationOf(outbound))
                .baggage(summarizeBaggage(first.baggageInformations()))
                .price(offer.price() != null ? offer.price().amount() : null)
                .currency(offer.price() != null ? offer.price().currency() : null)
                .build());
    }

    /** Segments of one leg, in payload order. A missing {@code route} means a one-way payload → outbound. */
    private List<TourVisioFlightItem> legSegments(List<TourVisioFlightItem> items, int route) {
        return items.stream()
                .filter(item -> item != null)
                .filter(item -> (item.route() != null ? item.route() : ROUTE_OUTBOUND) == route)
                .toList();
    }

    private Instant legDepartTime(List<TourVisioFlightItem> leg) {
        if (leg.isEmpty() || leg.get(0).departure() == null) {
            return null;
        }
        return toInstant(leg.get(0).departure().date());
    }

    private Instant legArriveTime(List<TourVisioFlightItem> leg) {
        if (leg.isEmpty()) {
            return null;
        }
        TourVisioFlightPoint arrival = leg.get(leg.size() - 1).arrival();
        return arrival == null ? null : toInstant(arrival.date());
    }

    /** Each connection between two segments is a stop, on top of any stop a segment reports itself. */
    private int stopsOf(List<TourVisioFlightItem> leg) {
        return leg.stream().mapToInt(TourVisioFlightItem::stopCount).sum() + leg.size() - 1;
    }

    private int durationOf(List<TourVisioFlightItem> leg) {
        return leg.stream().mapToInt(TourVisioFlightItem::duration).sum();
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

    /**
     * TourVisio sends timestamps either bare ("2026-08-01T10:00:00", meaning local time in the
     * configured airport timezone) or with an explicit offset ("2021-08-20T06:45:00Z"). Honour the
     * offset when one is present, otherwise apply the configured zone.
     */
    private Instant toInstant(String date) {
        if (date == null) {
            return null;
        }
        if (tourVisioProperties.timezone() == null || tourVisioProperties.timezone().isBlank()) {
            throw new DateTimeException("Missing tourvisio timezone");
        }
        TemporalAccessor parsed = DateTimeFormatter.ISO_DATE_TIME
                .parseBest(date, OffsetDateTime::from, LocalDateTime::from);
        if (parsed instanceof OffsetDateTime offsetDateTime) {
            return offsetDateTime.toInstant();
        }
        ZoneId zone = ZoneId.of(tourVisioProperties.timezone());
        return ((LocalDateTime) parsed).atZone(zone).toInstant();
    }
}
