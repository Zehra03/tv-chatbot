package com.paximum.paxassist.hotel.dto;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The cache key is the price-correctness boundary: two searches share a key only when TourVisio
 * would quote them the same price. Anything that changes the quote must change the key.
 */
class HotelSearchRequestCacheKeyTest {

    private static HotelSearchRequest search(List<Integer> childAges, String nationality) {
        return new HotelSearchRequest("Antalya", "2026-08-01", 7, 2, childAges, nationality, "TRY", "tr-TR");
    }

    @Test
    void differentChildAges_produceDifferentKeys() {
        // A family with a 10-year-old is not priced like a solo couple — they must not share a key.
        assertThat(search(List.of(10), "TR").cacheKey())
                .isNotEqualTo(search(List.of(), "TR").cacheKey());
        assertThat(search(List.of(4), "TR").cacheKey())
                .isNotEqualTo(search(List.of(10), "TR").cacheKey());
    }

    @Test
    void differentNationality_producesDifferentKey() {
        assertThat(search(List.of(), "DE").cacheKey())
                .isNotEqualTo(search(List.of(), "TR").cacheKey());
    }

    @Test
    void childAgeOrderDoesNotSplitTheKey() {
        // Same party, listed differently — one cache entry, not two.
        assertThat(search(List.of(10, 4), "TR").cacheKey())
                .isEqualTo(search(List.of(4, 10), "TR").cacheKey());
    }

    @Test
    void equivalentCheckInFormats_shareOneKey() {
        HotelSearchRequest padded = new HotelSearchRequest(
                "Antalya", " 2026-08-01 ", 7, 2, List.of(), "TR", "TRY", "tr-TR");
        assertThat(padded.cacheKey()).isEqualTo(search(List.of(), "TR").cacheKey());
    }

    @Test
    void destinationAndCurrencyCaseDoNotSplitTheKey() {
        HotelSearchRequest shouty = new HotelSearchRequest(
                "ANTALYA", "2026-08-01", 7, 2, List.of(), "tr", "try", "tr-TR");
        assertThat(shouty.cacheKey()).isEqualTo(search(List.of(), "TR").cacheKey());
    }

    @Test
    void differentStayLength_producesDifferentKey() {
        HotelSearchRequest longer = new HotelSearchRequest(
                "Antalya", "2026-08-01", 10, 2, List.of(), "TR", "TRY", "tr-TR");
        assertThat(longer.cacheKey()).isNotEqualTo(search(List.of(), "TR").cacheKey());
    }

    @Test
    void incompleteRequest_stillProducesAKeyInsteadOfThrowing() {
        // @Cacheable builds the key before the method runs, so a half-filled slot must not NPE —
        // otherwise the service could never answer with its missing-parameter prompt.
        HotelSearchRequest incomplete = new HotelSearchRequest(
                null, null, null, null, null, null, null, null);

        assertThat(incomplete.cacheKey()).isNotBlank();
    }
}
