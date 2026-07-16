package com.paximum.paxassist.flight.infrastructure.mapper;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.paximum.paxassist.flight.config.TourVisioProperties;
import com.paximum.paxassist.flight.domain.FlightProduct;
import com.paximum.paxassist.flight.domain.TripType;
import com.paximum.paxassist.flight.infrastructure.dto.response.TourVisioFlightResult;
import com.paximum.paxassist.flight.infrastructure.dto.response.TourVisioOffer;
import com.paximum.paxassist.flight.infrastructure.dto.response.TourVisioPriceSearchResponse;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Drives the mapper with TourVisio's own documented price-search payloads (copied verbatim into
 * {@code src/test/resources/tourvisio}), because the bug this code exists to fix was an assumption
 * about the wire format — one no hand-written fixture would have caught: a round-trip search returns
 * the outbound and the return as SEPARATE results, each priced and tokenised on its own.
 */
class TourVisioFlightResponseMapperContractTest {

    private final ObjectMapper json = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private final TourVisioFlightResponseMapper mapper =
            new TourVisioFlightResponseMapper(new TourVisioProperties(
                    "http://localhost", "tr-TR", "Europe/Istanbul", "agency", "user", "password"));

    private TourVisioPriceSearchResponse fixture(String name) throws Exception {
        try (var stream = getClass().getResourceAsStream("/tourvisio/" + name)) {
            assertThat(stream).as("fixture %s", name).isNotNull();
            return json.readValue(stream, TourVisioPriceSearchResponse.class);
        }
    }

    private Set<String> offerIdsOfLeg(TourVisioPriceSearchResponse response, int route) {
        return response.body().flights().stream()
                .filter(flight -> flight.items().get(0).route() == route)
                .flatMap(flight -> flight.allOffers().stream())
                .flatMap(offer -> offer.offerIds() != null
                        ? offer.offerIds().stream().map(id -> id.offerId())
                        : java.util.stream.Stream.of(offer.offerId()))
                .collect(Collectors.toSet());
    }

    @Test
    void buildsOneRoundTripPerOutbound_pairingItWithAReturnLeg() throws Exception {
        TourVisioPriceSearchResponse response = fixture("pricesearch-roundtrip-listtype3.json");

        List<FlightProduct> products = mapper.toFlightProducts(response, TripType.ROUND_TRIP);

        // The payload holds one outbound leg and one return leg — that is ONE trip, not two flights.
        assertThat(products).hasSize(1);
        FlightProduct trip = products.get(0);
        assertThat(trip.getOrigin()).isEqualTo("IST");
        assertThat(trip.getDestination()).isEqualTo("AYT");
    }

    /** The regression: the return leg used to be dropped, leaving a "round trip" with no return. */
    @Test
    void carriesTheReturnLeg() throws Exception {
        TourVisioPriceSearchResponse response = fixture("pricesearch-roundtrip-listtype3.json");

        FlightProduct trip = mapper.toFlightProducts(response, TripType.ROUND_TRIP).get(0);

        assertThat(trip.getReturnDepartTime()).isEqualTo(Instant.parse("2021-08-27T07:50:00Z"));
        assertThat(trip.getReturnArriveTime()).isEqualTo(Instant.parse("2021-08-27T09:00:00Z"));
        assertThat(trip.getReturnAirline()).isEqualTo("TK");
        assertThat(trip.getReturnStops()).isZero();
    }

    /** Booking with the outbound token alone would buy a one-way, so both must survive mapping. */
    @Test
    void carriesABookingTokenForEachLeg_takenFromTheRightLeg() throws Exception {
        TourVisioPriceSearchResponse response = fixture("pricesearch-roundtrip-listtype3.json");

        FlightProduct trip = mapper.toFlightProducts(response, TripType.ROUND_TRIP).get(0);

        assertThat(trip.getOfferId()).isIn(offerIdsOfLeg(response, 1));
        assertThat(trip.getReturnOfferId()).isIn(offerIdsOfLeg(response, 2));
        assertThat(trip.getReturnOfferId()).isNotEqualTo(trip.getOfferId());
    }

    /** Cheapest outbound fare (107.78) + cheapest return fare (110.43); neither offer is packaged. */
    @Test
    void pricesTheTripAsTheSumOfBothLegsWhenTheFareIsNotPackaged() throws Exception {
        TourVisioPriceSearchResponse response = fixture("pricesearch-roundtrip-listtype3.json");

        FlightProduct trip = mapper.toFlightProducts(response, TripType.ROUND_TRIP).get(0);

        assertThat(trip.getPrice()).isEqualByComparingTo(new BigDecimal("218.21"));
        assertThat(trip.getCurrency()).isEqualTo("EUR");
    }

    @Test
    void pricesAPackagedTripAsTheDearerLegRatherThanTheSum() throws Exception {
        TourVisioPriceSearchResponse response = packagedVariantOf("pricesearch-roundtrip-listtype3.json");

        FlightProduct trip = mapper.toFlightProducts(response, TripType.ROUND_TRIP).get(0);

        // A packaged offer already covers the whole trip on each leg: max(107.78, 110.43).
        assertThat(trip.getPrice()).isEqualByComparingTo(new BigDecimal("110.43"));
    }

    @Test
    void mapsEachOneWayFlightWithNoReturnLeg() throws Exception {
        TourVisioPriceSearchResponse response = fixture("pricesearch-oneway.json");

        List<FlightProduct> products = mapper.toFlightProducts(response, TripType.ONE_WAY);

        assertThat(products).hasSize(2);
        assertThat(products).allSatisfy(product -> {
            assertThat(product.getReturnOfferId()).isNull();
            assertThat(product.getReturnDepartTime()).isNull();
            assertThat(product.getOfferId()).isNotNull();
            assertThat(product.getPrice()).isNotNull();
        });
    }

    /** The legacy payload states no group keys and one flat token per leg; pairing still works. */
    @Test
    void stillPairsTheLegsOfALegacyPayload() throws Exception {
        TourVisioPriceSearchResponse response = fixture("pricesearch-roundtrip-legacy.json");

        List<FlightProduct> products = mapper.toFlightProducts(response, TripType.ROUND_TRIP);

        assertThat(products).hasSize(1);
        FlightProduct trip = products.get(0);
        assertThat(trip.getReturnOfferId()).isNotNull().isNotEqualTo(trip.getOfferId());
        assertThat(trip.getReturnDepartTime()).isNotNull();
        assertThat(trip.getPrice()).isEqualByComparingTo(new BigDecimal("160.82")); // 80.41 + 80.41
    }

    @Test
    void returnsNothingForAnEmptyOrBrokenPayload() {
        assertThat(mapper.toFlightProducts(null, TripType.ONE_WAY)).isEmpty();
        assertThat(mapper.toFlightProducts(new TourVisioPriceSearchResponse(null, null), TripType.ONE_WAY)).isEmpty();
    }

    /** Same payload, every offer flagged as packaged. */
    private TourVisioPriceSearchResponse packagedVariantOf(String name) throws Exception {
        TourVisioPriceSearchResponse original = fixture(name);
        List<TourVisioFlightResult> flights = original.body().flights().stream()
                .map(flight -> new TourVisioFlightResult(
                        flight.id(),
                        flight.items(),
                        flight.offer(),
                        flight.allOffers().stream()
                                .map(offer -> new TourVisioOffer(offer.offerId(), offer.offerIds(),
                                        offer.groupKeys(), true, offer.price()))
                                .toList()))
                .toList();
        return new TourVisioPriceSearchResponse(
                original.header(),
                new com.paximum.paxassist.flight.infrastructure.dto.response.TourVisioResponseBody(
                        original.body().searchId(), flights));
    }
}
