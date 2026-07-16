package com.paximum.paxassist.flight.infrastructure.mapper;

import java.math.BigDecimal;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAccessor;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
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

/**
 * Turns a TourVisio price search into {@link FlightProduct} cards.
 *
 * <p>The provider does not return trips, it returns <b>legs</b>: a round-trip search answers with
 * the outbound flights and the return flights as separate results ({@code items[].route} 1 vs 2),
 * each carrying its own priced offers. So a round trip is rebuilt here by pairing an outbound offer
 * with a return offer that shares a group key — the provider's own rule for which fares may be sold
 * together — and the pair's two booking tokens both travel on the product, because booking with the
 * outbound token alone would buy a one-way.
 *
 * <p>Every combinable (outbound, return) pair becomes one option, priced with the cheapest fares that
 * pair allows — so the chat can first offer the outbounds and then, once one is chosen, the returns
 * that actually fly with it. Nothing here fabricates an id or splits a price.
 */
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
        List<TourVisioFlightResult> flights = response.body().flights();
        List<TourVisioFlightResult> outbounds = legsOfRoute(flights, ROUTE_OUTBOUND);

        if (requestedTripType != TripType.ROUND_TRIP) {
            return outbounds.stream()
                    .map(outbound -> mapSafely(outbound, () -> toOneWay(outbound)))
                    .flatMap(Optional::stream)
                    .toList();
        }

        List<TourVisioFlightResult> returns = legsOfRoute(flights, ROUTE_RETURN);
        if (returns.isEmpty()) {
            // Asked for a round trip and got no return legs at all: show the outbounds rather than
            // nothing, but never dressed up as a round trip (no return times, outbound price only).
            log.warn("Round-trip search returned no return (route={}) legs; mapping outbounds only", ROUTE_RETURN);
            return outbounds.stream()
                    .map(outbound -> mapSafely(outbound, () -> toOneWay(outbound)))
                    .flatMap(Optional::stream)
                    .toList();
        }

        return outbounds.stream()
                .flatMap(outbound -> returns.stream()
                        .map(returnLeg -> mapSafely(outbound, () -> toRoundTrip(outbound, returnLeg)))
                        .flatMap(Optional::stream))
                .toList();
    }

    /** A leg belongs to a route when its segments do; a payload with no {@code route} is outbound. */
    private List<TourVisioFlightResult> legsOfRoute(List<TourVisioFlightResult> flights, int route) {
        return flights.stream()
                .filter(flight -> flight != null && flight.items() != null && !flight.items().isEmpty())
                .filter(flight -> routeOf(flight) == route)
                .toList();
    }

    private int routeOf(TourVisioFlightResult flight) {
        Integer route = flight.items().get(0).route();
        return route != null ? route : ROUTE_OUTBOUND;
    }

    /** A single unparsable result must not sink the whole search. */
    private Optional<FlightProduct> mapSafely(TourVisioFlightResult flight,
                                              java.util.function.Supplier<Optional<FlightProduct>> mapping) {
        try {
            return mapping.get();
        } catch (DateTimeException e) {
            log.warn("Skipping flight result {} due to unparsable date/timezone data: {}",
                    flight.id(), e.getMessage());
            return Optional.empty();
        }
    }

    private Optional<FlightProduct> toOneWay(TourVisioFlightResult outbound) {
        TourVisioOffer offer = cheapestOffer(outbound.allOffers()).orElse(null);
        if (offer == null || priceOf(offer) == null) {
            log.warn("Skipping flight result {}: no priced offer", outbound.id());
            return Optional.empty();
        }
        return baseBuilder(outbound, offer.offerIdFor(null))
                .map(builder -> builder
                        .price(priceOf(offer))
                        .currency(currencyOf(offer))
                        .build());
    }

    /**
     * One trip flying out on {@code outbound} and back on {@code returnLeg}, at the cheapest fares
     * the two allow. Empty when the provider does not let these legs be sold together: a return is
     * combinable only when one of its offers shares a group key with an outbound offer, and that
     * shared key then names the booking token to use on each side.
     */
    private Optional<FlightProduct> toRoundTrip(TourVisioFlightResult outbound, TourVisioFlightResult returnLeg) {
        Pairing best = null;
        for (TourVisioOffer outboundOffer : outbound.allOffers()) {
            for (TourVisioOffer returnOffer : returnLeg.allOffers()) {
                Pairing pairing = pair(outboundOffer, returnLeg, returnOffer);
                if (pairing != null && (best == null || pairing.total().compareTo(best.total()) < 0)) {
                    best = pairing;
                }
            }
        }
        if (best == null) {
            return Optional.empty();
        }

        List<TourVisioFlightItem> returnSegments = returnLeg.items();
        Pairing paired = best;
        return baseBuilder(outbound, best.outboundOfferId())
                .map(builder -> builder
                        // A pair needs its own id: several returns share one outbound, and a card id
                        // that repeated across them could not tell the options apart.
                        .id(outbound.id() + "::" + returnLeg.id())
                        .returnLegId(returnLeg.id())
                        .returnOfferId(paired.returnOfferId())
                        .returnDepartTime(legDepartTime(returnSegments))
                        .returnArriveTime(legArriveTime(returnSegments))
                        .returnAirline(airlineOf(returnSegments))
                        .returnStops(stopsOf(returnSegments))
                        .price(paired.total())
                        .currency(paired.currency())
                        .build());
    }

    /** Null when these two offers may not be sold together, or either lacks a usable price/token. */
    private Pairing pair(TourVisioOffer outboundOffer, TourVisioFlightResult returnLeg, TourVisioOffer returnOffer) {
        BigDecimal outboundPrice = priceOf(outboundOffer);
        BigDecimal returnPrice = priceOf(returnOffer);
        if (outboundPrice == null || returnPrice == null) {
            return null;
        }
        String groupKey;
        if (grouped(outboundOffer) && grouped(returnOffer)) {
            groupKey = sharedGroupKey(outboundOffer, returnOffer);
            if (groupKey == null) {
                return null; // the provider does not allow these two fares to be sold together
            }
        } else {
            // A legacy payload states no grouping rules, so there is none to honour: the legs pair
            // on their own flat tokens.
            groupKey = null;
        }
        String outboundOfferId = outboundOffer.offerIdFor(groupKey);
        String returnOfferId = returnOffer.offerIdFor(groupKey);
        if (outboundOfferId == null || returnOfferId == null) {
            return null;
        }
        // A packaged offer already prices the whole trip on each leg, so the trip costs the dearer
        // of the two rather than their sum.
        BigDecimal total = outboundOffer.packaged() || returnOffer.packaged()
                ? outboundPrice.max(returnPrice)
                : outboundPrice.add(returnPrice);
        String currency = currencyOf(outboundOffer) != null ? currencyOf(outboundOffer) : currencyOf(returnOffer);
        return new Pairing(returnLeg, outboundOfferId, returnOfferId, total, currency);
    }

    private boolean grouped(TourVisioOffer offer) {
        return offer.groupKeys() != null && !offer.groupKeys().isEmpty();
    }

    /** The first group key both offers carry, or null when they share none. */
    private String sharedGroupKey(TourVisioOffer outboundOffer, TourVisioOffer returnOffer) {
        Set<String> shared = new LinkedHashSet<>(outboundOffer.groupKeys());
        shared.retainAll(returnOffer.groupKeys());
        return shared.stream().findFirst().orElse(null);
    }

    /** The outbound half of a product: everything that does not depend on the return leg. */
    private Optional<FlightProduct.FlightProductBuilder> baseBuilder(TourVisioFlightResult outbound, String offerId) {
        if (offerId == null) {
            log.warn("Skipping flight result {}: offer carries no booking token", outbound.id());
            return Optional.empty();
        }
        List<TourVisioFlightItem> segments = outbound.items();
        TourVisioFlightItem first = segments.get(0);
        TourVisioFlightItem last = segments.get(segments.size() - 1);
        if (first.departure() == null || first.departure().airport() == null
                || last.arrival() == null || last.arrival().airport() == null) {
            log.warn("Skipping flight result {} due to missing departure/arrival airport data", outbound.id());
            return Optional.empty();
        }
        return Optional.of(FlightProduct.builder()
                .id(outbound.id())
                .outboundLegId(outbound.id())
                .offerId(offerId)
                .airline(airlineOf(segments))
                .flightNumber(FlightNumberParser.parse(first.flightNo()).number())
                .origin(first.departure().airport().id())
                .destination(last.arrival().airport().id())
                .originCity(cityNameOf(first.departure()))
                .destinationCity(cityNameOf(last.arrival()))
                .departTime(toInstant(first.departure().date()))
                .arriveTime(toInstant(last.arrival().date()))
                .stops(stopsOf(segments))
                .durationMinutes(durationOf(segments))
                .baggage(summarizeBaggage(first.baggageInformations())));
    }

    /** The cheapest fare for a leg — the "from" price a one-way card shows. */
    private Optional<TourVisioOffer> cheapestOffer(List<TourVisioOffer> offers) {
        return offers.stream()
                .filter(offer -> priceOf(offer) != null)
                .min((a, b) -> priceOf(a).compareTo(priceOf(b)));
    }

    private BigDecimal priceOf(TourVisioOffer offer) {
        return offer.price() != null ? offer.price().amount() : null;
    }

    private String currencyOf(TourVisioOffer offer) {
        return offer.price() != null ? offer.price().currency() : null;
    }

    private String airlineOf(List<TourVisioFlightItem> leg) {
        TourVisioFlightItem first = leg.get(0);
        return first.airline() != null ? first.airline().internationalCode() : null;
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

    /** One outbound-offer/return-offer combination the provider allows, with its trip total. */
    private record Pairing(TourVisioFlightResult returnLeg, String outboundOfferId, String returnOfferId,
                           BigDecimal total, String currency) {
    }
}
