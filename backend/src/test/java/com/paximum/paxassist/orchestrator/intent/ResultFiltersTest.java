package com.paximum.paxassist.orchestrator.intent;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.paximum.paxassist.hotel.HotelProduct;

import static org.assertj.core.api.Assertions.assertThat;

class ResultFiltersTest {

    private HotelProduct hotel(String id, String price, String board) {
        BigDecimal p = price == null ? null : new BigDecimal(price);
        return new HotelProduct(id, "Hotel " + id, "Antalya", 5, p, "TRY", board, true);
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
}
