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
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.paximum.paxassist.flight.config.TourVisioProperties;
import com.paximum.paxassist.flight.domain.BaggageAllowance;
import com.paximum.paxassist.flight.domain.FlightProduct;
import com.paximum.paxassist.flight.domain.FlightSearchCriteria;
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
 * <p>A round-trip search comes back in one of two shapes, and both are handled:
 * <ul>
 *   <li><b>A result per leg</b> — the outbound flights and the return flights arrive as separate
 *       results ({@code items[].route} 1 vs 2), each priced by its own offers. A trip is rebuilt by
 *       pairing an outbound offer with a return offer that shares a group key (the provider's own
 *       rule for which fares may be sold together), and the pair's two booking tokens both travel
 *       on the product, because booking with the outbound token alone would buy a one-way.</li>
 *   <li><b>A result per trip</b> — one result holds both legs in its segments and one offer buys
 *       them together, so the product carries a single token.</li>
 * </ul>
 * Which shape arrives depends on the route, so neither may be assumed: reading a per-trip result as
 * a leg is what produced AYT→AYT cards with no return to choose.
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

    /**
     * @param criteria the search these results answer. Beyond the trip type, its baggage request
     *                 decides WHICH fare of a flight becomes the card: see {@link #cheapestOffer}.
     */
    public List<FlightProduct> toFlightProducts(TourVisioPriceSearchResponse response,
                                                FlightSearchCriteria criteria) {
        if (response == null || response.body() == null || response.body().flights() == null) {
            return List.of();
        }
        List<TourVisioFlightResult> flights = response.body().flights();
        List<TourVisioFlightResult> outbounds = legsOfRoute(flights, ROUTE_OUTBOUND);

        if (criteria.getTripType() != TripType.ROUND_TRIP) {
            return outbounds.stream()
                    .map(outbound -> mapSafely(outbound, () -> toOneWay(outbound, criteria)))
                    .flatMap(Optional::stream)
                    .toList();
        }

        // Shape 1: one result per bookable round trip, both legs in its segments.
        List<TourVisioFlightResult> combined = flights.stream()
                .filter(flight -> flight != null && flight.items() != null && !flight.items().isEmpty())
                .filter(this::carriesBothLegs)
                .toList();
        if (!combined.isEmpty()) {
            return combined.stream()
                    .map(trip -> mapSafely(trip, () -> toCombinedRoundTrip(trip, criteria)))
                    .flatMap(Optional::stream)
                    .toList();
        }

        // Shape 2: a result per leg — rebuild trips by pairing them.
        List<TourVisioFlightResult> returns = legsOfRoute(flights, ROUTE_RETURN);
        if (returns.isEmpty()) {
            // Asked for a round trip and got no return legs at all: show the outbounds rather than
            // nothing, but never dressed up as a round trip (no return times, outbound price only).
            log.warn("Round-trip search returned no return (route={}) legs; mapping outbounds only", ROUTE_RETURN);
            return outbounds.stream()
                    .map(outbound -> mapSafely(outbound, () -> toOneWay(outbound, criteria)))
                    .flatMap(Optional::stream)
                    .toList();
        }

        return outbounds.stream()
                .flatMap(outbound -> returns.stream()
                        .map(returnLeg -> mapSafely(outbound, () -> toRoundTrip(outbound, returnLeg, criteria)))
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

    /** A segment with no {@code route} is outbound, matching {@link TourVisioFlightItem}. */
    private static int routeOf(TourVisioFlightItem segment) {
        return segment.route() != null ? segment.route() : ROUTE_OUTBOUND;
    }

    /** Only the segments of one leg, in provider order — a layover keeps several of them. */
    private List<TourVisioFlightItem> segmentsOfRoute(TourVisioFlightResult flight, int route) {
        return flight.items().stream()
                .filter(segment -> routeOf(segment) == route)
                .toList();
    }

    /**
     * True when one result already holds the whole trip: its segments carry route 1 <b>and</b> 2.
     *
     * <p>The provider sends both shapes. Sometimes a leg per result (paired here via group keys);
     * sometimes — as on AYT⇄ADB — a single result per bookable round trip, whose one offer buys
     * both legs. Classifying by {@code items[0].route} alone reads the second shape as an outbound,
     * which left no returns to pair, no return to choose, and an origin/destination spanning the
     * trip instead of the leg.
     */
    private boolean carriesBothLegs(TourVisioFlightResult flight) {
        boolean out = false;
        boolean back = false;
        for (TourVisioFlightItem segment : flight.items()) {
            if (routeOf(segment) == ROUTE_OUTBOUND) {
                out = true;
            } else if (routeOf(segment) == ROUTE_RETURN) {
                back = true;
            }
        }
        return out && back;
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

    private Optional<FlightProduct> toOneWay(TourVisioFlightResult outbound, FlightSearchCriteria criteria) {
        // Outbound segments only: a one-way request can still be answered with a combined result,
        // and its return segments would otherwise land the card back where it started.
        List<TourVisioFlightItem> segments = segmentsOfRoute(outbound, ROUTE_OUTBOUND);
        TourVisioOffer offer = cheapestOffer(outbound.allOffers(), segments, criteria).orElse(null);
        if (offer == null || priceOf(offer) == null) {
            log.debug("No offer of flight result {} is both priced and within the baggage request",
                    outbound.id());
            return Optional.empty();
        }
        return baseBuilder(outbound, segments, offer.offerIdFor(null), baggageLinesOf(offer, segments))
                .map(builder -> builder
                        .price(priceOf(offer))
                        .currency(currencyOf(offer))
                        .build());
    }

    /**
     * A round trip the provider sells as one result: a single offer buys both legs, so there is no
     * second token to carry and no group key to agree on. The card's origin/destination describe the
     * outbound; the return leg fills the return fields.
     */
    private Optional<FlightProduct> toCombinedRoundTrip(TourVisioFlightResult trip, FlightSearchCriteria criteria) {
        List<TourVisioFlightItem> outboundSegments = segmentsOfRoute(trip, ROUTE_OUTBOUND);
        TourVisioOffer offer = cheapestOffer(trip.allOffers(), outboundSegments, criteria).orElse(null);
        if (offer == null || priceOf(offer) == null) {
            log.debug("No offer of flight result {} is both priced and within the baggage request", trip.id());
            return Optional.empty();
        }
        List<TourVisioFlightItem> returnSegments = segmentsOfRoute(trip, ROUTE_RETURN);
        return baseBuilder(trip, outboundSegments, offer.offerIdFor(null),
                baggageLinesOf(offer, outboundSegments))
                .map(builder -> builder
                        // Trips that fly the same outbound must share an outbound id, or the chat's
                        // "pick an outbound, then a return" step would offer one return per outbound.
                        .outboundLegId(outboundKey(trip))
                        .returnLegId(trip.id())
                        // Deliberately no returnOfferId: the reservation sends [offerId, returnOfferId]
                        // when it is set, and repeating this trip's single token would book it twice.
                        .returnDepartTime(legDepartTime(returnSegments))
                        .returnArriveTime(legArriveTime(returnSegments))
                        .returnAirline(airlineOf(returnSegments))
                        .returnStops(stopsOf(returnSegments))
                        .price(priceOf(offer))
                        .currency(currencyOf(offer))
                        .build());
    }

    /**
     * Identity of a combined result's outbound leg. The provider gives no id for a leg inside a
     * trip, so the flight numbers and departure time — what makes it that flight — stand in.
     */
    private String outboundKey(TourVisioFlightResult trip) {
        return segmentsOfRoute(trip, ROUTE_OUTBOUND).stream()
                .map(segment -> segment.flightNo() + "@"
                        + (segment.departure() != null ? segment.departure().date() : "?"))
                .collect(Collectors.joining("+"));
    }

    /**
     * One trip flying out on {@code outbound} and back on {@code returnLeg}, at the cheapest fares
     * the two allow. Empty when the provider does not let these legs be sold together: a return is
     * combinable only when one of its offers shares a group key with an outbound offer, and that
     * shared key then names the booking token to use on each side.
     */
    private Optional<FlightProduct> toRoundTrip(TourVisioFlightResult outbound, TourVisioFlightResult returnLeg,
                                                FlightSearchCriteria criteria) {
        List<TourVisioFlightItem> outboundSegments = segmentsOfRoute(outbound, ROUTE_OUTBOUND);
        List<TourVisioFlightItem> returnSegments = segmentsOfRoute(returnLeg, ROUTE_RETURN);

        Pairing best = null;
        for (TourVisioOffer outboundOffer : outbound.allOffers()) {
            // Both legs must meet the baggage request: a trip whose return drops to 15 kg is not a
            // "20 kg" trip, and the traveller flies it in both directions.
            if (!allowanceOf(outboundOffer, outboundSegments).satisfies(
                    criteria.getCheckedBaggage(), criteria.getMinCheckedBaggageKg())) {
                continue;
            }
            for (TourVisioOffer returnOffer : returnLeg.allOffers()) {
                if (!allowanceOf(returnOffer, returnSegments).satisfies(
                        criteria.getCheckedBaggage(), criteria.getMinCheckedBaggageKg())) {
                    continue;
                }
                Pairing pairing = pair(outboundOffer, returnLeg, returnOffer, outboundSegments);
                if (pairing != null && (best == null || pairing.total().compareTo(best.total()) < 0)) {
                    best = pairing;
                }
            }
        }
        if (best == null) {
            return Optional.empty();
        }

        Pairing paired = best;
        return baseBuilder(outbound, outboundSegments, best.outboundOfferId(), best.outboundBaggage())
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

    /**
     * Null when these two offers may not be sold together, or either lacks a usable price/token.
     *
     * @param outboundSegments the outbound leg's segments, the fallback source of its baggage lines
     *                         when the offer states none (legacy payloads)
     */
    private Pairing pair(TourVisioOffer outboundOffer, TourVisioFlightResult returnLeg, TourVisioOffer returnOffer,
                         List<TourVisioFlightItem> outboundSegments) {
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
        return new Pairing(returnLeg, outboundOfferId, returnOfferId, total, currency,
                baggageLinesOf(outboundOffer, outboundSegments));
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

    /**
     * The outbound half of a product: everything that does not depend on the return leg.
     *
     * <p>{@code segments} is passed in rather than read from {@code outbound.items()} because a
     * result does not always hold exactly one leg — see {@link #carriesBothLegs}. Reading all of a
     * combined result's items here is what produced AYT→AYT cards: the first segment departs the
     * origin and the last one arrives back at it.
     */
    private Optional<FlightProduct.FlightProductBuilder> baseBuilder(TourVisioFlightResult outbound,
                                                                     List<TourVisioFlightItem> segments,
                                                                     String offerId,
                                                                     List<TourVisioBaggageInfo> baggageLines) {
        if (offerId == null) {
            log.warn("Skipping flight result {}: offer carries no booking token", outbound.id());
            return Optional.empty();
        }
        if (segments.isEmpty()) {
            log.warn("Skipping flight result {}: no segments for the leg", outbound.id());
            return Optional.empty();
        }
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
                .baggage(summarizeBaggage(baggageLines))
                .baggageAllowance(toAllowance(baggageLines)));
    }

    /**
     * The cheapest fare for a leg that also meets the traveller's baggage request — the "from" price
     * a card shows.
     *
     * <p>The baggage request narrows the candidates rather than filtering the finished cards, because
     * baggage belongs to the fare: the same flight sells as 15 kg for less and 20 kg for more. Asked
     * for 20 kg, dropping the flight because its CHEAPEST fare carries 15 kg would hide a 20 kg fare
     * that exists; so the cheapest fare that does carry 20 kg is priced instead. Every card therefore
     * remains a real, buyable fare — no price or allowance is invented.
     */
    private Optional<TourVisioOffer> cheapestOffer(List<TourVisioOffer> offers,
                                                   List<TourVisioFlightItem> segments,
                                                   FlightSearchCriteria criteria) {
        return offers.stream()
                .filter(offer -> priceOf(offer) != null)
                .filter(offer -> allowanceOf(offer, segments)
                        .satisfies(criteria.getCheckedBaggage(), criteria.getMinCheckedBaggageKg()))
                .min((a, b) -> priceOf(a).compareTo(priceOf(b)));
    }

    /**
     * The baggage lines that describe what this offer's price buys. The offer's own list wins; a
     * legacy payload carries none, and then the leg's first segment is the only statement available.
     */
    private List<TourVisioBaggageInfo> baggageLinesOf(TourVisioOffer offer, List<TourVisioFlightItem> segments) {
        if (offer.baggageInformations() != null && !offer.baggageInformations().isEmpty()) {
            return offer.baggageInformations();
        }
        return segments.isEmpty() ? List.of() : nullSafe(segments.get(0).baggageInformations());
    }

    private BaggageAllowance allowanceOf(TourVisioOffer offer, List<TourVisioFlightItem> segments) {
        return toAllowance(baggageLinesOf(offer, segments));
    }

    /**
     * Reads the provider's baggage lines as the allowance a filter can act on. No lines at all means
     * unknown — never "no baggage": the two are told apart so an unverifiable fare is not advertised
     * as meeting a baggage request (see {@link BaggageAllowance}).
     */
    private BaggageAllowance toAllowance(List<TourVisioBaggageInfo> lines) {
        if (lines == null || lines.isEmpty()) {
            return BaggageAllowance.unknown();
        }
        List<TourVisioBaggageInfo> checked = lines.stream()
                .filter(TourVisioBaggageInfo::isChecked)
                .filter(TourVisioBaggageInfo::grantsAllowance)
                .toList();
        if (checked.isEmpty()) {
            // The provider listed this fare's baggage and none of it is checked — a cabin-only fare.
            return new BaggageAllowance(false, null);
        }
        // The largest stated allowance: several lines are the pieces of one fare's allowance, and a
        // piece-based line contributes no kg to compare.
        Integer kg = checked.stream()
                .map(TourVisioBaggageInfo::weightInKg)
                .filter(java.util.Objects::nonNull)
                .max(Integer::compareTo)
                .orElse(null);
        return new BaggageAllowance(true, kg);
    }

    private List<TourVisioBaggageInfo> nullSafe(List<TourVisioBaggageInfo> lines) {
        return lines == null ? List.of() : lines;
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
                           BigDecimal total, String currency,
                           List<TourVisioBaggageInfo> outboundBaggage) {
    }
}
