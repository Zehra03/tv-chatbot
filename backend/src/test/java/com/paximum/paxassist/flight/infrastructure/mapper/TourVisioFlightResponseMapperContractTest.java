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
import com.paximum.paxassist.flight.domain.FlightSearchCriteria;
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

    /** A search with no baggage request, so every priced fare stays a candidate. */
    private static FlightSearchCriteria criteriaFor(TripType tripType) {
        return FlightSearchCriteria.builder().tripType(tripType).build();
    }

    private static FlightSearchCriteria criteriaWithMinBaggage(TripType tripType, int minKg) {
        return FlightSearchCriteria.builder().tripType(tripType).minCheckedBaggageKg(minKg).build();
    }

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

        List<FlightProduct> products = mapper.toFlightProducts(response, criteriaFor(TripType.ROUND_TRIP));

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

        FlightProduct trip = mapper.toFlightProducts(response, criteriaFor(TripType.ROUND_TRIP)).get(0);

        assertThat(trip.getReturnDepartTime()).isEqualTo(Instant.parse("2021-08-27T07:50:00Z"));
        assertThat(trip.getReturnArriveTime()).isEqualTo(Instant.parse("2021-08-27T09:00:00Z"));
        assertThat(trip.getReturnAirline()).isEqualTo("TK");
        assertThat(trip.getReturnStops()).isZero();
    }

    /** Booking with the outbound token alone would buy a one-way, so both must survive mapping. */
    @Test
    void carriesABookingTokenForEachLeg_takenFromTheRightLeg() throws Exception {
        TourVisioPriceSearchResponse response = fixture("pricesearch-roundtrip-listtype3.json");

        FlightProduct trip = mapper.toFlightProducts(response, criteriaFor(TripType.ROUND_TRIP)).get(0);

        assertThat(trip.getOfferId()).isIn(offerIdsOfLeg(response, 1));
        assertThat(trip.getReturnOfferId()).isIn(offerIdsOfLeg(response, 2));
        assertThat(trip.getReturnOfferId()).isNotEqualTo(trip.getOfferId());
    }

    /** Cheapest outbound fare (107.78) + cheapest return fare (110.43); neither offer is packaged. */
    @Test
    void pricesTheTripAsTheSumOfBothLegsWhenTheFareIsNotPackaged() throws Exception {
        TourVisioPriceSearchResponse response = fixture("pricesearch-roundtrip-listtype3.json");

        FlightProduct trip = mapper.toFlightProducts(response, criteriaFor(TripType.ROUND_TRIP)).get(0);

        assertThat(trip.getPrice()).isEqualByComparingTo(new BigDecimal("218.21"));
        assertThat(trip.getCurrency()).isEqualTo("EUR");
    }

    @Test
    void pricesAPackagedTripAsTheDearerLegRatherThanTheSum() throws Exception {
        TourVisioPriceSearchResponse response = packagedVariantOf("pricesearch-roundtrip-listtype3.json");

        FlightProduct trip = mapper.toFlightProducts(response, criteriaFor(TripType.ROUND_TRIP)).get(0);

        // A packaged offer already covers the whole trip on each leg: max(107.78, 110.43).
        assertThat(trip.getPrice()).isEqualByComparingTo(new BigDecimal("110.43"));
    }

    @Test
    void mapsEachOneWayFlightWithNoReturnLeg() throws Exception {
        TourVisioPriceSearchResponse response = fixture("pricesearch-oneway.json");

        List<FlightProduct> products = mapper.toFlightProducts(response, criteriaFor(TripType.ONE_WAY));

        assertThat(products).hasSize(2);
        assertThat(products).allSatisfy(product -> {
            assertThat(product.getReturnOfferId()).isNull();
            assertThat(product.getReturnDepartTime()).isNull();
            assertThat(product.getOfferId()).isNotNull();
            assertThat(product.getPrice()).isNotNull();
        });
    }

    /**
     * The provider's own one-way payload sells TK7516 three ways: ECO FLY 21.27 with 15 kg,
     * EXTRA FLY 24.76 with 20 kg, PRIME FLY 29.41 with 20 kg. With no baggage asked for, the card is
     * the cheapest fare — and its baggage must be that fare's 15 kg, not another's.
     */
    @Test
    void withNoBaggageRequest_pricesTheCheapestFareAndReportsThatFaresBaggage() throws Exception {
        TourVisioPriceSearchResponse response = fixture("pricesearch-oneway.json");

        FlightProduct flight = mapper.toFlightProducts(response, criteriaFor(TripType.ONE_WAY)).get(0);

        assertThat(flight.getPrice()).isEqualByComparingTo(new BigDecimal("21.27"));
        assertThat(flight.getBaggageAllowance().checkedIncluded()).isTrue();
        assertThat(flight.getBaggageAllowance().checkedKg()).isEqualTo(15);
    }

    /**
     * The point of doing this in the search rather than over the finished cards: the cheapest fare
     * carries 15 kg, so filtering cards would have dropped TK7516 entirely and reported "no 20 kg
     * flight" — while a 20 kg fare for it exists at 24.76.
     */
    @Test
    void withA20KgRequest_pricesTheCheapestFareThatActuallyCarries20Kg() throws Exception {
        TourVisioPriceSearchResponse response = fixture("pricesearch-oneway.json");

        List<FlightProduct> products = mapper.toFlightProducts(response, criteriaWithMinBaggage(TripType.ONE_WAY, 20));

        assertThat(products).isNotEmpty();
        assertThat(products).allSatisfy(product -> {
            assertThat(product.getBaggageAllowance().checkedKg()).isGreaterThanOrEqualTo(20);
            // EXTRA FLY (24.76), not PRIME FLY (29.41): still the cheapest fare that qualifies.
            assertThat(product.getPrice()).isEqualByComparingTo(new BigDecimal("24.76"));
        });
    }

    @Test
    void aThresholdNoFareMeets_yieldsNoFlightRatherThanADearerOne() throws Exception {
        TourVisioPriceSearchResponse response = fixture("pricesearch-oneway.json");

        assertThat(mapper.toFlightProducts(response, criteriaWithMinBaggage(TripType.ONE_WAY, 30))).isEmpty();
    }

    /** The legacy payload states no group keys and one flat token per leg; pairing still works. */
    @Test
    void stillPairsTheLegsOfALegacyPayload() throws Exception {
        TourVisioPriceSearchResponse response = fixture("pricesearch-roundtrip-legacy.json");

        List<FlightProduct> products = mapper.toFlightProducts(response, criteriaFor(TripType.ROUND_TRIP));

        assertThat(products).hasSize(1);
        FlightProduct trip = products.get(0);
        assertThat(trip.getReturnOfferId()).isNotNull().isNotEqualTo(trip.getOfferId());
        assertThat(trip.getReturnDepartTime()).isNotNull();
        assertThat(trip.getPrice()).isEqualByComparingTo(new BigDecimal("160.82")); // 80.41 + 80.41
    }

    @Test
    void returnsNothingForAnEmptyOrBrokenPayload() {
        assertThat(mapper.toFlightProducts(null, criteriaFor(TripType.ONE_WAY))).isEmpty();
        assertThat(mapper.toFlightProducts(new TourVisioPriceSearchResponse(null, null), criteriaFor(TripType.ONE_WAY))).isEmpty();
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
                                        offer.groupKeys(), true, offer.price(),
                                        offer.baggageInformations()))
                                .toList()))
                .toList();
        return new TourVisioPriceSearchResponse(
                original.header(),
                new com.paximum.paxassist.flight.infrastructure.dto.response.TourVisioResponseBody(
                        original.body().searchId(), flights));
    }
}
