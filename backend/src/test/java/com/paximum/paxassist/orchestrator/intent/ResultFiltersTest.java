package com.paximum.paxassist.orchestrator.intent;

import java.math.BigDecimal;
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

    // ── applyAirline ─────────────────────────────────────────────────────────

    @Test
    void airlineNull_returnsListUnchanged() {
        FlightProduct f = FlightProduct.builder().airline("THY").build();
        List<Object> cards = List.of(f);
        assertThat(ResultFilters.applyAirline(cards, null)).isSameAs(cards);
    }

    @Test
    void airline_matchesCaseInsensitive() {
        FlightProduct thy = FlightProduct.builder().airline("Turkish Airlines").build();
        FlightProduct pegasus = FlightProduct.builder().airline("Pegasus").build();
        HotelProduct hotel = hotel("H1", "100", "AI");
        
        List<Object> result = ResultFilters.applyAirline(List.of(thy, pegasus, hotel), "turkish");
        assertThat(result).containsExactly(thy, hotel);
        
        List<Object> result2 = ResultFilters.applyAirline(List.of(thy, pegasus, hotel), "PEGASUS");
        assertThat(result2).containsExactly(pegasus, hotel);
    }

    // ── applyDepartureTime ───────────────────────────────────────────────────

    @Test
    void departureTimeNull_returnsListUnchanged() {
        FlightProduct f = FlightProduct.builder().departTime(java.time.Instant.now()).build();
        List<Object> cards = List.of(f);
        assertThat(ResultFilters.applyDepartureTime(cards, null, null)).isSameAs(cards);
    }

    @Test
    void departureTime_filtersCorrectly() {
        // 08:00 UTC = 11:00 TRT (Europe/Istanbul is UTC+3)
        // 13:00 UTC = 16:00 TRT
        // 18:00 UTC = 21:00 TRT
        java.time.Instant morningUtc = java.time.Instant.parse("2026-07-16T08:00:00Z");
        java.time.Instant afternoonUtc = java.time.Instant.parse("2026-07-16T13:00:00Z");
        java.time.Instant eveningUtc = java.time.Instant.parse("2026-07-16T18:00:00Z");

        FlightProduct morningFlight = FlightProduct.builder().departTime(morningUtc).build(); // 11:00 TRT
        FlightProduct afternoonFlight = FlightProduct.builder().departTime(afternoonUtc).build(); // 16:00 TRT
        FlightProduct eveningFlight = FlightProduct.builder().departTime(eveningUtc).build(); // 21:00 TRT
        HotelProduct hotel = hotel("H1", "100", "AI");

        List<Object> allCards = List.of(morningFlight, afternoonFlight, eveningFlight, hotel);

        // Before 12:00 TRT
        List<Object> result1 = ResultFilters.applyDepartureTime(allCards, null, "12:00");
        assertThat(result1).containsExactly(morningFlight, hotel);

        // Between 15:00 and 17:00 TRT
        List<Object> result2 = ResultFilters.applyDepartureTime(allCards, "15:00", "17:00");
        assertThat(result2).containsExactly(afternoonFlight, hotel);

        // After 20:00 TRT
        List<Object> result3 = ResultFilters.applyDepartureTime(allCards, "20:00", null);
        assertThat(result3).containsExactly(eveningFlight, hotel);
    }
}
