package com.paximum.paxassist.flight.infrastructure.mapper;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.paximum.paxassist.flight.config.TourVisioProperties;
import com.paximum.paxassist.flight.domain.FlightProduct;
import com.paximum.paxassist.flight.domain.TripType;
import com.paximum.paxassist.flight.infrastructure.dto.response.TourVisioAirline;
import com.paximum.paxassist.flight.infrastructure.dto.response.TourVisioAirport;
import com.paximum.paxassist.flight.infrastructure.dto.response.TourVisioCity;
import com.paximum.paxassist.flight.infrastructure.dto.response.TourVisioFlightItem;
import com.paximum.paxassist.flight.infrastructure.dto.response.TourVisioFlightPoint;
import com.paximum.paxassist.flight.infrastructure.dto.response.TourVisioFlightResult;
import com.paximum.paxassist.flight.infrastructure.dto.response.TourVisioOffer;
import com.paximum.paxassist.flight.infrastructure.dto.response.TourVisioPrice;
import com.paximum.paxassist.flight.infrastructure.dto.response.TourVisioPriceSearchResponse;
import com.paximum.paxassist.flight.infrastructure.dto.response.TourVisioResponseBody;
import com.paximum.paxassist.flight.infrastructure.dto.response.TourVisioResponseHeader;

import static org.assertj.core.api.Assertions.assertThat;

class TourVisioFlightResponseMapperTest {

    private static final TourVisioProperties VALID_PROPERTIES =
            new TourVisioProperties("https://test.example.com", "en-US", "Europe/Istanbul", "a", "u", "p");

    private static final int ROUTE_OUTBOUND = 1;
    private static final int ROUTE_RETURN = 2;

    private TourVisioFlightItem segment(Integer route, String from, String fromCity, String departDate,
                                        String to, String toCity, String arriveDate) {
        return new TourVisioFlightItem(
                "TK123",
                route,
                120,
                0,
                new TourVisioAirline("TK", "Turkish Airlines"),
                new TourVisioFlightPoint(new TourVisioAirport(from), departDate, new TourVisioCity(from, fromCity)),
                new TourVisioFlightPoint(new TourVisioAirport(to), arriveDate, new TourVisioCity(to, toCity)),
                List.of());
    }

    private TourVisioFlightItem validItem() {
        return segment(ROUTE_OUTBOUND, "IST", "Istanbul", "2026-08-01T10:00:00",
                "LHR", "London", "2026-08-01T12:00:00");
    }

    /** A leg priced by a single flat-token offer (the legacy payload shape). */
    private TourVisioFlightResult legWith(String id, String offerId, TourVisioFlightItem... items) {
        return new TourVisioFlightResult(
                id,
                List.of(items),
                new TourVisioOffer(offerId, null, null, null, new TourVisioPrice(BigDecimal.TEN, "USD")),
                null);
    }

    private TourVisioFlightResult flightWith(TourVisioFlightItem... items) {
        return legWith("flight-1", "offer-1", items);
    }

    private TourVisioFlightResult validFlight(TourVisioFlightItem item) {
        return flightWith(item);
    }

    private TourVisioPriceSearchResponse responseWith(TourVisioFlightResult... flights) {
        return new TourVisioPriceSearchResponse(
                new TourVisioResponseHeader(true),
                new TourVisioResponseBody("search-1", List.of(flights)));
    }

    @Test
    void toFlightProducts_skipsFlightWithNullDepartureAirport() {
        TourVisioFlightItem item = new TourVisioFlightItem(
                "TK123", ROUTE_OUTBOUND, 120, 0,
                new TourVisioAirline("TK", "Turkish Airlines"),
                new TourVisioFlightPoint(null, "2026-08-01T10:00:00", null),
                new TourVisioFlightPoint(new TourVisioAirport("LHR"), "2026-08-01T12:00:00", null),
                List.of());
        TourVisioFlightResponseMapper mapper = new TourVisioFlightResponseMapper(VALID_PROPERTIES);

        List<FlightProduct> products = mapper.toFlightProducts(responseWith(validFlight(item)), TripType.ONE_WAY);

        assertThat(products).isEmpty();
    }

    @Test
    void toFlightProducts_skipsFlightWithNullArrivalAirport() {
        TourVisioFlightItem item = new TourVisioFlightItem(
                "TK123", ROUTE_OUTBOUND, 120, 0,
                new TourVisioAirline("TK", "Turkish Airlines"),
                new TourVisioFlightPoint(new TourVisioAirport("IST"), "2026-08-01T10:00:00", null),
                new TourVisioFlightPoint(null, "2026-08-01T12:00:00", null),
                List.of());
        TourVisioFlightResponseMapper mapper = new TourVisioFlightResponseMapper(VALID_PROPERTIES);

        List<FlightProduct> products = mapper.toFlightProducts(responseWith(validFlight(item)), TripType.ONE_WAY);

        assertThat(products).isEmpty();
    }

    @Test
    void toFlightProducts_skipsFlightWhenTimezoneMissing() {
        TourVisioProperties blankTimezoneProperties =
                new TourVisioProperties("https://test.example.com", "en-US", "  ", "a", "u", "p");
        TourVisioFlightResponseMapper mapper = new TourVisioFlightResponseMapper(blankTimezoneProperties);

        List<FlightProduct> products =
                mapper.toFlightProducts(responseWith(validFlight(validItem())), TripType.ONE_WAY);

        assertThat(products).isEmpty();
    }

    @Test
    void toFlightProducts_mapsValidFlightWithCorrectInstant() {
        TourVisioFlightResponseMapper mapper = new TourVisioFlightResponseMapper(VALID_PROPERTIES);

        List<FlightProduct> products =
                mapper.toFlightProducts(responseWith(validFlight(validItem())), TripType.ONE_WAY);

        assertThat(products).hasSize(1);
        FlightProduct product = products.get(0);
        assertThat(product.getId()).isEqualTo("flight-1");
        assertThat(product.getOfferId()).isEqualTo("offer-1");
        assertThat(product.getOrigin()).isEqualTo("IST");
        assertThat(product.getDestination()).isEqualTo("LHR");
        assertThat(product.getOriginCity()).isEqualTo("Istanbul");
        assertThat(product.getDestinationCity()).isEqualTo("London");
        assertThat(product.getAirline()).isEqualTo("TK");
        assertThat(product.getDepartTime()).isEqualTo(Instant.parse("2026-08-01T07:00:00Z"));
        assertThat(product.getReturnDepartTime()).isNull();
        assertThat(product.getReturnArriveTime()).isNull();
    }

    /** An item with no {@code route} (one-way payload) still maps as the outbound leg. */
    @Test
    void toFlightProducts_treatsMissingRouteAsOutbound() {
        TourVisioFlightItem item = segment(null, "IST", "Istanbul", "2026-08-01T10:00:00",
                "LHR", "London", "2026-08-01T12:00:00");
        TourVisioFlightResponseMapper mapper = new TourVisioFlightResponseMapper(VALID_PROPERTIES);

        List<FlightProduct> products = mapper.toFlightProducts(responseWith(flightWith(item)), TripType.ONE_WAY);

        assertThat(products).hasSize(1);
        assertThat(products.get(0).getOrigin()).isEqualTo("IST");
        assertThat(products.get(0).getReturnDepartTime()).isNull();
    }

    @Test
    void toFlightProducts_pairsTheOutboundAndReturnResultsIntoOneTrip() {
        TourVisioFlightItem outbound = segment(ROUTE_OUTBOUND, "IST", "Istanbul", "2026-08-20T06:45:00",
                "AYT", "Antalya", "2026-08-20T07:55:00");
        TourVisioFlightItem inbound = segment(ROUTE_RETURN, "AYT", "Antalya", "2026-08-27T07:50:00",
                "IST", "Istanbul", "2026-08-27T09:00:00");
        TourVisioFlightResponseMapper mapper = new TourVisioFlightResponseMapper(VALID_PROPERTIES);

        List<FlightProduct> products = mapper.toFlightProducts(
                responseWith(legWith("out-1", "offer-out", outbound), legWith("in-1", "offer-in", inbound)),
                TripType.ROUND_TRIP);

        assertThat(products).hasSize(1);
        FlightProduct product = products.get(0);
        assertThat(product.getOrigin()).isEqualTo("IST");
        assertThat(product.getDestination()).isEqualTo("AYT");
        assertThat(product.getDepartTime()).isEqualTo(Instant.parse("2026-08-20T03:45:00Z"));
        assertThat(product.getArriveTime()).isEqualTo(Instant.parse("2026-08-20T04:55:00Z"));
        assertThat(product.getReturnDepartTime()).isEqualTo(Instant.parse("2026-08-27T04:50:00Z"));
        assertThat(product.getReturnArriveTime()).isEqualTo(Instant.parse("2026-08-27T06:00:00Z"));
        assertThat(product.getOfferId()).isEqualTo("offer-out");
        assertThat(product.getReturnOfferId()).isEqualTo("offer-in");
    }

    /**
     * The other shape the provider sends (seen live on AYT⇄ADB): ONE result per bookable trip,
     * holding both legs in its segments and priced by one offer. Read as a leg — which is what
     * classifying on {@code items[0].route} did — the card spanned the whole trip and came out
     * AYT→AYT, with no return leg for the chat to offer.
     */
    @Test
    void toFlightProducts_mapsATripThatArrivesAsOneResultWithBothLegs() {
        TourVisioFlightItem outbound = segment(ROUTE_OUTBOUND, "AYT", "Antalya", "2026-07-18T09:00:00",
                "ADB", "Izmir", "2026-07-18T10:10:00");
        TourVisioFlightItem inbound = segment(ROUTE_RETURN, "ADB", "Izmir", "2026-07-21T18:00:00",
                "AYT", "Antalya", "2026-07-21T19:10:00");
        TourVisioFlightResponseMapper mapper = new TourVisioFlightResponseMapper(VALID_PROPERTIES);

        List<FlightProduct> products = mapper.toFlightProducts(
                responseWith(legWith("trip-1", "offer-trip", outbound, inbound)), TripType.ROUND_TRIP);

        assertThat(products).hasSize(1);
        FlightProduct product = products.get(0);
        // The card describes the OUTBOUND, not the round trip's start and end.
        assertThat(product.getOrigin()).isEqualTo("AYT");
        assertThat(product.getDestination()).isEqualTo("ADB");
        assertThat(product.getDepartTime()).isEqualTo(Instant.parse("2026-07-18T06:00:00Z"));
        assertThat(product.getArriveTime()).isEqualTo(Instant.parse("2026-07-18T07:10:00Z"));
        // ...and the return leg is present, so there is something to fly home on.
        assertThat(product.getReturnDepartTime()).isEqualTo(Instant.parse("2026-07-21T15:00:00Z"));
        assertThat(product.getReturnArriveTime()).isEqualTo(Instant.parse("2026-07-21T16:10:00Z"));
        assertThat(product.getOfferId()).isEqualTo("offer-trip");
        // One offer buys both legs; a second token would book this trip twice.
        assertThat(product.getReturnOfferId()).isNull();
    }

    /** Trips flying the same outbound must share an outbound id, or the chat's step 1 is meaningless. */
    @Test
    void toFlightProducts_givesCombinedTripsSharingAnOutboundTheSameOutboundId() {
        TourVisioFlightItem outbound = segment(ROUTE_OUTBOUND, "AYT", "Antalya", "2026-07-18T09:00:00",
                "ADB", "Izmir", "2026-07-18T10:10:00");
        TourVisioFlightItem earlyReturn = segment(ROUTE_RETURN, "ADB", "Izmir", "2026-07-21T08:00:00",
                "AYT", "Antalya", "2026-07-21T09:10:00");
        TourVisioFlightItem lateReturn = segment(ROUTE_RETURN, "ADB", "Izmir", "2026-07-21T18:00:00",
                "AYT", "Antalya", "2026-07-21T19:10:00");
        TourVisioFlightResponseMapper mapper = new TourVisioFlightResponseMapper(VALID_PROPERTIES);

        List<FlightProduct> products = mapper.toFlightProducts(
                responseWith(legWith("trip-1", "offer-1", outbound, earlyReturn),
                        legWith("trip-2", "offer-2", outbound, lateReturn)),
                TripType.ROUND_TRIP);

        assertThat(products).hasSize(2);
        assertThat(products.get(0).getOutboundLegId()).isEqualTo(products.get(1).getOutboundLegId());
        // Each trip stays individually selectable.
        assertThat(products.get(0).getId()).isNotEqualTo(products.get(1).getId());
    }

    /** A one-way request answered with a combined result must not land back at the origin. */
    @Test
    void toFlightProducts_usesOnlyTheOutboundWhenAOneWayAnswerCarriesBothLegs() {
        TourVisioFlightItem outbound = segment(ROUTE_OUTBOUND, "AYT", "Antalya", "2026-07-18T09:00:00",
                "ADB", "Izmir", "2026-07-18T10:10:00");
        TourVisioFlightItem inbound = segment(ROUTE_RETURN, "ADB", "Izmir", "2026-07-21T18:00:00",
                "AYT", "Antalya", "2026-07-21T19:10:00");
        TourVisioFlightResponseMapper mapper = new TourVisioFlightResponseMapper(VALID_PROPERTIES);

        List<FlightProduct> products = mapper.toFlightProducts(
                responseWith(legWith("trip-1", "offer-trip", outbound, inbound)), TripType.ONE_WAY);

        assertThat(products).hasSize(1);
        assertThat(products.get(0).getOrigin()).isEqualTo("AYT");
        assertThat(products.get(0).getDestination()).isEqualTo("ADB");
        assertThat(products.get(0).getReturnDepartTime()).isNull();
    }

    /** A layover splits a leg into several segments: it departs with the first and lands with the last. */
    @Test
    void toFlightProducts_collapsesLayoverSegmentsIntoOneLegPerDirection() {
        TourVisioFlightItem outboundLeg1 = segment(ROUTE_OUTBOUND, "IST", "Istanbul", "2026-08-20T06:45:00",
                "VIE", "Vienna", "2026-08-20T08:45:00");
        TourVisioFlightItem outboundLeg2 = segment(ROUTE_OUTBOUND, "VIE", "Vienna", "2026-08-20T10:00:00",
                "LHR", "London", "2026-08-20T11:30:00");
        TourVisioFlightItem inboundLeg1 = segment(ROUTE_RETURN, "LHR", "London", "2026-08-27T07:50:00",
                "VIE", "Vienna", "2026-08-27T09:20:00");
        TourVisioFlightItem inboundLeg2 = segment(ROUTE_RETURN, "VIE", "Vienna", "2026-08-27T10:30:00",
                "IST", "Istanbul", "2026-08-27T13:00:00");
        TourVisioFlightResponseMapper mapper = new TourVisioFlightResponseMapper(VALID_PROPERTIES);

        List<FlightProduct> products = mapper.toFlightProducts(
                responseWith(legWith("out-1", "offer-out", outboundLeg1, outboundLeg2),
                        legWith("in-1", "offer-in", inboundLeg1, inboundLeg2)),
                TripType.ROUND_TRIP);

        assertThat(products).hasSize(1);
        FlightProduct product = products.get(0);
        assertThat(product.getOrigin()).isEqualTo("IST");
        assertThat(product.getDestination()).isEqualTo("LHR");
        assertThat(product.getDestinationCity()).isEqualTo("London");
        assertThat(product.getDepartTime()).isEqualTo(Instant.parse("2026-08-20T03:45:00Z"));
        assertThat(product.getArriveTime()).isEqualTo(Instant.parse("2026-08-20T08:30:00Z"));
        assertThat(product.getReturnDepartTime()).isEqualTo(Instant.parse("2026-08-27T04:50:00Z"));
        assertThat(product.getReturnArriveTime()).isEqualTo(Instant.parse("2026-08-27T10:00:00Z"));
        // Two outbound segments = one connection, so the nonstop filter must not treat this as direct.
        assertThat(product.getStops()).isEqualTo(1);
        assertThat(product.getDurationMinutes()).isEqualTo(240);
    }

    /** TourVisio also sends offset-bearing timestamps ("...Z"); those must not be re-zoned. */
    @Test
    void toFlightProducts_honoursExplicitOffsetInTimestamps() {
        TourVisioFlightItem outbound = segment(ROUTE_OUTBOUND, "IST", "Istanbul", "2026-08-20T06:45:00Z",
                "AYT", "Antalya", "2026-08-20T07:55:00Z");
        TourVisioFlightItem inbound = segment(ROUTE_RETURN, "AYT", "Antalya", "2026-08-27T07:50:00Z",
                "IST", "Istanbul", "2026-08-27T09:00:00Z");
        TourVisioFlightResponseMapper mapper = new TourVisioFlightResponseMapper(VALID_PROPERTIES);

        List<FlightProduct> products = mapper.toFlightProducts(
                responseWith(legWith("out-1", "offer-out", outbound), legWith("in-1", "offer-in", inbound)),
                TripType.ROUND_TRIP);

        assertThat(products).hasSize(1);
        assertThat(products.get(0).getDepartTime()).isEqualTo(Instant.parse("2026-08-20T06:45:00Z"));
        assertThat(products.get(0).getReturnDepartTime()).isEqualTo(Instant.parse("2026-08-27T07:50:00Z"));
    }
}
