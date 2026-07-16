package com.paximum.paxassist.orchestrator.intent;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.paximum.paxassist.flight.domain.FlightProduct;
import com.paximum.paxassist.hotel.HotelProduct;

import static org.assertj.core.api.Assertions.assertThat;

class ResultFiltersTest {

    private HotelProduct hotel(String id, String price, String board) {
        BigDecimal p = price == null ? null : new BigDecimal(price);
        return new HotelProduct(id, "Hotel " + id, "Antalya", 5, p, "TRY", board, true);
    }

    private HotelProduct hotelWith(String id, String... features) {
        return new HotelProduct(id, "Hotel " + id, "Antalya", 5, new BigDecimal("1000"), "TRY", "AI", true,
                null, List.of(features));
    }

    // ── applyMaxPrice ────────────────────────────────────────────────────────

    @Test
    void maxPriceNull_returnsListUnchanged() {
        List<Object> cards = List.of(hotel("A", "1000", "AI"));
        assertThat(ResultFilters.applyMaxPrice(cards, null)).isSameAs(cards);
    }

    @Test
    void maxPrice_dropsCardsAboveLimit_keepsBoundary() {
        HotelProduct cheap = hotel("A", "1500", "AI");
        HotelProduct atLimit = hotel("B", "1800", "AI");
        HotelProduct pricey = hotel("C", "2000", "AI");
        List<Object> result = ResultFilters.applyMaxPrice(List.of(cheap, atLimit, pricey), 1800);
        assertThat(result).containsExactly(cheap, atLimit);
    }

    @Test
    void maxPrice_keepsCardsWithUnknownPrice() {
        HotelProduct noPrice = hotel("A", null, "AI");
        HotelProduct pricey = hotel("B", "2000", "AI");
        List<Object> result = ResultFilters.applyMaxPrice(List.of(noPrice, pricey), 1000);
        assertThat(result).containsExactly(noPrice);
    }

    @Test
    void maxPrice_canReturnEmptyWhenNothingFits() {
        List<Object> result = ResultFilters.applyMaxPrice(List.of(hotel("A", "2000", "AI")), 10);
        assertThat(result).isEmpty();
    }

    // ── applyBoardType ───────────────────────────────────────────────────────

    @Test
    void boardTypeNull_returnsListUnchanged() {
        List<Object> cards = List.of(hotel("A", "1000", "Herşey Dahil"));
        assertThat(ResultFilters.applyBoardType(cards, null)).isSameAs(cards);
    }

    @Test
    void boardType_HB_matchesYarimPansiyon() {
        HotelProduct hb = hotel("A", "1000", "Yarım Pansiyon");
        HotelProduct ai = hotel("B", "2000", "Herşey Dahil");
        assertThat(ResultFilters.applyBoardType(List.of(hb, ai), "HB")).containsExactly(hb);
    }

    @Test
    void boardType_BB_matchesOdaKahvalti_notRoomOnly() {
        HotelProduct bb = hotel("A", "1000", "Oda Kahvaltı");
        HotelProduct ro = hotel("B", "900", "Sadece Oda");
        assertThat(ResultFilters.applyBoardType(List.of(bb, ro), "BB")).containsExactly(bb);
    }

    @Test
    void boardType_RO_doesNotMatchOdaKahvalti() {
        HotelProduct bb = hotel("A", "1000", "Oda Kahvaltı");
        HotelProduct ro = hotel("B", "900", "Room Only");
        assertThat(ResultFilters.applyBoardType(List.of(bb, ro), "RO")).containsExactly(ro);
    }

    @Test
    void boardType_neverEmptiesList_whenNothingMatches() {
        // All boards "Unknown" → filter can't confirm any → return original rather than hiding all.
        List<Object> cards = List.of(hotel("A", "1000", "Unknown"), hotel("B", "2000", "Unknown"));
        assertThat(ResultFilters.applyBoardType(cards, "AI")).isSameAs(cards);
    }

    @Test
    void boardType_unrecognizedCode_doesNotFilter() {
        List<Object> cards = List.of(hotel("A", "1000", "Herşey Dahil"), hotel("B", "2000", "Yarım Pansiyon"));
        assertThat(ResultFilters.applyBoardType(cards, "XYZ")).containsExactlyElementsOf(cards);
    }

    // ── applyFeatures ────────────────────────────────────────────────────────

    @Test
    void featuresNull_returnsListUnchanged() {
        List<Object> cards = List.of(hotelWith("A", "Beach Hotel"));
        assertThat(ResultFilters.applyFeatures(cards, null)).isSameAs(cards);
    }

    @Test
    void seafront_keepsOnlyHotelsWithBeachOrSeaData() {
        HotelProduct beach = hotelWith("A", "Beach Hotel", "Private Beach", "Outdoor Pool");
        HotelProduct sea100 = hotelWith("B", "100m to Sea", "Sand Beach");
        HotelProduct denizTheme = hotelWith("C", "Deniz Kenarında");
        HotelProduct city = hotelWith("D", "City Hotel", "Indoor Pool");
        List<Object> result = ResultFilters.applyFeatures(List.of(beach, sea100, denizTheme, city), List.of("SEAFRONT"));
        assertThat(result).containsExactly(beach, sea100, denizTheme);
    }

    @Test
    void features_canReturnEmpty_whenNoneMatch() {
        // Hard constraint: for "denize sıfır" we show only confirmed matches, never pretend.
        List<Object> cards = List.of(hotelWith("A", "City Hotel"), hotelWith("B", "Business Center"));
        assertThat(ResultFilters.applyFeatures(cards, List.of("SEAFRONT"))).isEmpty();
    }

    @Test
    void features_requireAllOfThem_andSemantics() {
        HotelProduct both = hotelWith("A", "Outdoor Pool", "Kids Club");
        HotelProduct poolOnly = hotelWith("B", "Outdoor Pool");
        List<Object> result = ResultFilters.applyFeatures(List.of(both, poolOnly), List.of("POOL", "KIDS_CLUB"));
        assertThat(result).containsExactly(both);
    }

    @Test
    void features_hotelWithNoProviderData_isNotConfirmed() {
        HotelProduct bare = hotelWith("A"); // no facilities/themes → cannot confirm seafront
        HotelProduct beach = hotelWith("B", "Sand Beach");
        assertThat(ResultFilters.applyFeatures(List.of(bare, beach), List.of("SEAFRONT"))).containsExactly(beach);
    }

    @Test
    void features_unrecognizedKey_doesNotFilter() {
        List<Object> cards = List.of(hotelWith("A", "City Hotel"), hotelWith("B", "Business Center"));
        assertThat(ResultFilters.applyFeatures(cards, List.of("HELIPAD"))).isSameAs(cards);
    }

    @Test
    void describeFeatures_mapsKeysToTurkishLabels() {
        assertThat(ResultFilters.describeFeatures(List.of("SEAFRONT", "POOL"))).isEqualTo("denize sıfır, havuzlu");
        assertThat(ResultFilters.describeFeatures(List.of("HELIPAD"))).isEmpty();
        assertThat(ResultFilters.describeFeatures(List.of())).isEmpty();
    }

    // ── applyDirectFlight ────────────────────────────────────────────────────

    @Test
    void directFlightNull_returnsListUnchanged() {
        FlightProduct f = FlightProduct.builder().stops(1).build();
        List<Object> cards = List.of(f);
        assertThat(ResultFilters.applyDirectFlight(cards, null)).isSameAs(cards);
    }

    @Test
    void directFlightTrue_keepsOnlyDirectFlights() {
        FlightProduct direct = FlightProduct.builder().stops(0).build();
        FlightProduct layover = FlightProduct.builder().stops(1).build();
        HotelProduct hotel = hotel("H1", "100", "AI");
        
        List<Object> result = ResultFilters.applyDirectFlight(List.of(direct, layover, hotel), true);
        assertThat(result).containsExactly(direct, hotel);
    }

    @Test
    void directFlightFalse_keepsOnlyLayoverFlights() {
        FlightProduct direct = FlightProduct.builder().stops(0).build();
        FlightProduct layover = FlightProduct.builder().stops(1).build();
        HotelProduct hotel = hotel("H1", "100", "AI");
        
        List<Object> result = ResultFilters.applyDirectFlight(List.of(direct, layover, hotel), false);
        assertThat(result).containsExactly(layover, hotel);
    }

    // ── applyDepartTimeRange ─────────────────────────────────────────────────

    /** A flight departing at the given local hour in Europe/Istanbul (the zone the filter reads). */
    private FlightProduct flightAt(String id, int istanbulHour) {
        Instant departs = LocalDate.of(2026, 7, 20).atTime(istanbulHour, 0)
                .atZone(ZoneId.of("Europe/Istanbul")).toInstant();
        return FlightProduct.builder().id(id).departTime(departs).stops(0).build();
    }

    @Test
    void nullOrUnknownBucket_returnsCardsUnchanged() {
        List<Object> cards = List.of(flightAt("F1", 8));
        assertThat(ResultFilters.applyDepartTimeRange(cards, null)).isSameAs(cards);
        assertThat(ResultFilters.applyDepartTimeRange(cards, "  ")).isSameAs(cards);
        assertThat(ResultFilters.applyDepartTimeRange(cards, "lunchtime")).isSameAs(cards);
    }

    @Test
    void morningKeepsOnlyMorningFlights() {
        FlightProduct morning = flightAt("F1", 8);    // 06-12
        FlightProduct afternoon = flightAt("F2", 14);
        HotelProduct hotel = hotel("H1", "100", "AI");

        List<Object> result = ResultFilters.applyDepartTimeRange(List.of(morning, afternoon, hotel), "morning");
        assertThat(result).containsExactly(morning, hotel); // hotels unaffected
    }

    @Test
    void boundariesAreInclusiveFromExclusiveTo() {
        assertThat(ResultFilters.applyDepartTimeRange(List.of(flightAt("F", 6)), "morning")).hasSize(1);   // 06 in
        assertThat(ResultFilters.applyDepartTimeRange(List.of(flightAt("F", 12)), "morning")).isEmpty();    // 12 out
        assertThat(ResultFilters.applyDepartTimeRange(List.of(flightAt("F", 12)), "afternoon")).hasSize(1); // 12 in
    }

    @Test
    void nightWrapsAroundMidnight() {
        assertThat(ResultFilters.applyDepartTimeRange(List.of(flightAt("F", 23)), "night")).hasSize(1); // late evening
        assertThat(ResultFilters.applyDepartTimeRange(List.of(flightAt("F", 3)), "night")).hasSize(1);  // small hours
        assertThat(ResultFilters.applyDepartTimeRange(List.of(flightAt("F", 12)), "night")).isEmpty();  // midday
    }

    @Test
    void flightWithNoDepartTimeIsDroppedWhenBucketRequested() {
        FlightProduct noTime = FlightProduct.builder().id("F1").stops(0).build();
        assertThat(ResultFilters.applyDepartTimeRange(List.of(noTime), "morning")).isEmpty();
    }

    @Test
    void explicitRangeIsInclusiveOnBothBounds() {
        assertThat(ResultFilters.applyDepartTimeRange(List.of(flightAt("F", 9)), "10:00-14:00")).isEmpty();   // before
        assertThat(ResultFilters.applyDepartTimeRange(List.of(flightAt("F", 10)), "10:00-14:00")).hasSize(1); // from (in)
        assertThat(ResultFilters.applyDepartTimeRange(List.of(flightAt("F", 14)), "10:00-14:00")).hasSize(1); // to (in)
        assertThat(ResultFilters.applyDepartTimeRange(List.of(flightAt("F", 15)), "10:00-14:00")).isEmpty();  // after
    }

    @Test
    void openEndedFromKeepsFlightsAtOrAfter() {
        assertThat(ResultFilters.applyDepartTimeRange(List.of(flightAt("F", 9)), "10:00-")).isEmpty();
        assertThat(ResultFilters.applyDepartTimeRange(List.of(flightAt("F", 10)), "10:00-")).hasSize(1);
        assertThat(ResultFilters.applyDepartTimeRange(List.of(flightAt("F", 20)), "10:00-")).hasSize(1);
    }

    @Test
    void openEndedToKeepsFlightsAtOrBefore() {
        assertThat(ResultFilters.applyDepartTimeRange(List.of(flightAt("F", 14)), "-14:00")).hasSize(1);
        assertThat(ResultFilters.applyDepartTimeRange(List.of(flightAt("F", 15)), "-14:00")).isEmpty();
    }

    @Test
    void explicitRangeAcceptsBareHours() {
        assertThat(ResultFilters.applyDepartTimeRange(List.of(flightAt("F", 12)), "10-14")).hasSize(1);
        assertThat(ResultFilters.applyDepartTimeRange(List.of(flightAt("F", 9)), "10-14")).isEmpty();
    }

    @Test
    void explicitRangeCanWrapMidnight() {
        assertThat(ResultFilters.applyDepartTimeRange(List.of(flightAt("F", 23)), "22:00-02:00")).hasSize(1);
        assertThat(ResultFilters.applyDepartTimeRange(List.of(flightAt("F", 1)), "22:00-02:00")).hasSize(1);
        assertThat(ResultFilters.applyDepartTimeRange(List.of(flightAt("F", 12)), "22:00-02:00")).isEmpty();
    }
}
