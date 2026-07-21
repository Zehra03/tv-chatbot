package com.paximum.paxassist.orchestrator.mapper;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

import com.paximum.paxassist.ai.SlotCriteria;
import com.paximum.paxassist.flight.domain.FlightSearchCriteria;
import com.paximum.paxassist.flight.domain.TripType;

import static org.assertj.core.api.Assertions.assertThat;

class FlightCriteriaMapperTest {

    private final FlightCriteriaMapper mapper = new FlightCriteriaMapper(new GeoCountryResolver());
    private final ObjectMapper objectMapper = new ObjectMapper();

    private SlotCriteria slots(Map<String, Object> fields) {
        return objectMapper.convertValue(fields, SlotCriteria.class);
    }

    @Test
    void oneWayWhenNoReturnDate() {
        FlightSearchCriteria criteria = mapper.toCriteria(slots(Map.of(
                "origin", "IST", "destination", "CDG", "departureDate", "2026-08-01", "adults", 1)));

        assertThat(criteria.getTripType()).isEqualTo(TripType.ONE_WAY);
        assertThat(criteria.getDepartDate()).isEqualTo(LocalDate.of(2026, 8, 1));
        assertThat(criteria.getReturnDate()).isNull();
    }

    @Test
    void roundTripWhenReturnDatePresent() {
        FlightSearchCriteria criteria = mapper.toCriteria(slots(Map.of(
                "origin", "IST", "destination", "CDG",
                "departureDate", "2026-08-01", "returnDate", "2026-08-10", "adults", 2)));

        assertThat(criteria.getTripType()).isEqualTo(TripType.ROUND_TRIP);
        assertThat(criteria.getReturnDate()).isEqualTo(LocalDate.of(2026, 8, 10));
    }

    /**
     * The bug this maps around: "gidiş-dönüş uçuş arıyorum" with no return date yet used to arrive
     * indistinguishable from a one-way search, so the user got one-way results instead of being
     * asked for the return date. The stated trip type now survives the missing date, and the flight
     * module reports the date as missing.
     */
    @Test
    void statedRoundTripSurvivesAMissingReturnDate_soTheChatCanAskForIt() {
        FlightSearchCriteria criteria = mapper.toCriteria(slots(Map.of(
                "origin", "IST", "destination", "CDG", "departureDate", "2026-08-01",
                "adults", 1, "currency", "TRY", "tripType", "round_trip")));

        assertThat(criteria.getTripType()).isEqualTo(TripType.ROUND_TRIP);
        assertThat(criteria.getReturnDate()).isNull();
        assertThat(criteria.missingRequiredFields()).containsExactly("returnDate");
    }

    @Test
    void statedOneWayWinsOverALeftoverReturnDate() {
        FlightSearchCriteria criteria = mapper.toCriteria(slots(Map.of(
                "origin", "IST", "destination", "CDG", "departureDate", "2026-08-01",
                "returnDate", "2026-08-10", "adults", 1, "tripType", "one_way")));

        assertThat(criteria.getTripType()).isEqualTo(TripType.ONE_WAY);
    }

    @Test
    void typesEachChildByAge_infantChildAndAdultFare() {
        // 1 → lap infant, 8 → child, 14 → pays the adult fare and joins the adult count.
        FlightSearchCriteria criteria = mapper.toCriteria(slots(Map.of(
                "adults", 2, "children", 3, "childAges", List.of(1, 8, 14))));

        assertThat(criteria.getPassengers().getInfants()).isEqualTo(1);
        assertThat(criteria.getPassengers().getChildren()).isEqualTo(1);
        assertThat(criteria.getPassengers().getAdults()).isEqualTo(3);
    }

    @Test
    void ageBoundariesFollowTheAirlineFareRule() {
        // The exact edges: 1 is still an infant, 2 is already a child, 11 is still a child,
        // 12 already pays the adult fare.
        FlightSearchCriteria criteria = mapper.toCriteria(slots(Map.of(
                "adults", 1, "children", 4, "childAges", List.of(1, 2, 11, 12))));

        assertThat(criteria.getPassengers().getInfants()).isEqualTo(1);
        assertThat(criteria.getPassengers().getChildren()).isEqualTo(2);
        assertThat(criteria.getPassengers().getAdults()).isEqualTo(2);
    }

    @Test
    void newbornIsAnInfant() {
        FlightSearchCriteria criteria = mapper.toCriteria(slots(Map.of(
                "adults", 1, "children", 1, "childAges", List.of(0))));

        assertThat(criteria.getPassengers().getInfants()).isEqualTo(1);
        assertThat(criteria.getPassengers().getChildren()).isZero();
    }

    @Test
    void adultsOnly_hasNoChildrenOrInfants() {
        FlightSearchCriteria criteria = mapper.toCriteria(slots(Map.of("adults", 2)));

        assertThat(criteria.getPassengers().getAdults()).isEqualTo(2);
        assertThat(criteria.getPassengers().getChildren()).isZero();
        assertThat(criteria.getPassengers().getInfants()).isZero();
    }

    @Test
    void childCountWithoutAgesIsNotTypedAsChild() {
        // A bare count cannot be typed, so nothing is assumed here: FlightSearchHandler asks for the
        // ages before the search runs, which is what stops an infant being priced as a child.
        FlightSearchCriteria criteria = mapper.toCriteria(slots(Map.of("adults", 2, "children", 1)));

        assertThat(criteria.getPassengers().getAdults()).isEqualTo(2);
        assertThat(criteria.getPassengers().getChildren()).isZero();
        assertThat(criteria.getPassengers().getInfants()).isZero();
    }

    @Test
    void passengersNullWhenNoAdults_soFlightModuleReportsItMissing() {
        FlightSearchCriteria criteria = mapper.toCriteria(slots(Map.of("origin", "IST")));

        assertThat(criteria.getPassengers()).isNull();
        assertThat(criteria.missingRequiredFields()).contains("passengers");
    }
}
