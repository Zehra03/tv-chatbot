package com.paximum.paxassist.flight.infrastructure.mapper;

import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.paximum.paxassist.flight.config.TourVisioProperties;
import com.paximum.paxassist.flight.domain.FlightProduct;
import com.paximum.paxassist.flight.domain.TripType;
import com.paximum.paxassist.flight.infrastructure.dto.response.TourVisioFlightItem;
import com.paximum.paxassist.flight.infrastructure.dto.response.TourVisioFlightResult;
import com.paximum.paxassist.flight.infrastructure.dto.response.TourVisioOffer;
import com.paximum.paxassist.flight.infrastructure.dto.response.TourVisioPriceSearchResponse;
import com.paximum.paxassist.flight.infrastructure.mapper.FlightNumberParser.ParsedFlightNumber;

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
                products.addAll(toFlightProducts(flight, requestedTripType));
            } catch (DateTimeException e) {
                log.warn("Skipping flight result {} due to unparsable date/timezone data: {}",
                        flight.id(), e.getMessage());
            }
        }
        return products;
    }

    private List<FlightProduct> toFlightProducts(TourVisioFlightResult flight, TripType requestedTripType) {
        if (flight.items() == null || flight.items().isEmpty() || flight.offers() == null) {
            return List.of();
        }

        TourVisioFlightItem firstLeg = flight.items().get(0);
        TourVisioFlightItem lastLeg = flight.items().get(flight.items().size() - 1);
        ParsedFlightNumber flightNumber = FlightNumberParser.parse(firstLeg.flightNo());

        if (requestedTripType == TripType.ROUND_TRIP) {
            // TourVisio's pricesearch response gives no signal (in the schema seen so far) for
            // distinguishing a return leg from outbound connection segments, so round-trip
            // results are mapped as outbound-only until a live round-trip sample is available.
            log.warn("Flight result {} has no resolvable return leg for a round-trip search; "
                    + "mapping outbound data only", flight.id());
        }

        List<FlightProduct> products = new ArrayList<>();
        for (TourVisioOffer offer : flight.offers()) {
            products.add(FlightProduct.builder()
                    .id(offer.offerId())
                    .airline(flightNumber.airlineCode())
                    .flightNumber(flightNumber.number())
                    .origin(firstLeg.departure().airport().id())
                    .destination(lastLeg.arrival().airport().id())
                    .departTime(toInstant(firstLeg.departure().date()))
                    .arriveTime(toInstant(lastLeg.arrival().date()))
                    .returnDepartTime(null)
                    .returnArriveTime(null)
                    .stops(flight.stopCount())
                    // Not present anywhere in the TourVisio pricesearch response.
                    .baggage(null)
                    .price(offer.price() != null ? offer.price().amount() : null)
                    .currency(offer.price() != null ? offer.price().currency() : null)
                    .build());
        }
        return products;
    }

    private Instant toInstant(String date) {
        if (date == null) {
            return null;
        }
        LocalDateTime localDateTime = LocalDateTime.parse(date, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        ZoneId zone = ZoneId.of(tourVisioProperties.timezone());
        return localDateTime.atZone(zone).toInstant();
    }
}