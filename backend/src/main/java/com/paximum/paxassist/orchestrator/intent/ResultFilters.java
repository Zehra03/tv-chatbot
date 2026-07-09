package com.paximum.paxassist.orchestrator.intent;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

import com.paximum.paxassist.hotel.HotelProduct;

/**
 * Post-search, in-memory filters applied to the result cards the handlers already fetched from
 * TourVisio. Kept package-private (like {@link ProductCards}) so the type-narrowing lives in one
 * place, and price reading is reused from {@link ProductCards#priceOf(Object)}.
 *
 * <p>These are honest filters over REAL results (no fabrication) — the search itself is unchanged,
 * we only drop cards that don't match a budget or board-type the user stated.
 */
final class ResultFilters {

    private ResultFilters() {
    }

    /**
     * Keep cards whose price is at most {@code maxPrice}. Cards with an unknown (null) price are
     * kept — we don't silently hide a result just because its price didn't map. Returns the list
     * unchanged when no budget was given. An empty result IS meaningful here (price is reliable),
     * so the caller can tell the user nothing fit the budget.
     */
    static List<Object> applyMaxPrice(List<Object> cards, Integer maxPrice) {
        if (maxPrice == null || cards == null || cards.isEmpty()) {
            return cards;
        }
        BigDecimal limit = BigDecimal.valueOf(maxPrice);
        return cards.stream()
                .filter(card -> {
                    BigDecimal price = ProductCards.priceOf(card);
                    return price == null || price.compareTo(limit) <= 0;
                })
                .collect(Collectors.toList());
    }

    /**
     * Keep hotel cards whose board name matches the requested board code (AI | HB | BB | RO).
     * Board metadata from TourVisio is a free-text name ("Herşey Dahil", "Yarım Pansiyon",
     * "Oda Kahvaltı", sometimes "Unknown") and unreliable, so this is best-effort keyword matching
     * and it NEVER empties the list: if nothing matches (e.g. all boards are "Unknown"), the
     * original list is returned rather than hiding every result.
     */
    static List<Object> applyBoardType(List<Object> cards, String boardType) {
        if (boardType == null || boardType.isBlank() || cards == null || cards.isEmpty()) {
            return cards;
        }
        List<Object> filtered = cards.stream()
                .filter(card -> matchesBoard(card, boardType))
                .collect(Collectors.toList());
        return filtered.isEmpty() ? cards : filtered;
    }

    private static boolean matchesBoard(Object card, String requested) {
        if (!(card instanceof HotelProduct hotel)) {
            return true; // board type only applies to hotels; leave other cards untouched
        }
        String board = hotel.boardType();
        if (board == null) {
            return false;
        }
        String b = board.toLowerCase(Locale.ROOT);
        return switch (requested.trim().toUpperCase(Locale.ROOT)) {
            case "AI" -> containsAny(b, "herşey", "her şey", "all inclusive", "ultra");
            case "HB" -> containsAny(b, "yarım", "half");
            case "BB" -> containsAny(b, "kahvaltı", "breakfast", "bed & breakfast", "bed and breakfast");
            // Room-only: check AFTER breakfast so "Oda Kahvaltı" (BB) is not mistaken for RO.
            case "RO" -> !containsAny(b, "kahvaltı", "breakfast")
                    && containsAny(b, "sadece oda", "room only", "oda", "room");
            default -> true; // unrecognized board code → don't filter anything out
        };
    }

    private static boolean containsAny(String haystack, String... needles) {
        for (String needle : needles) {
            if (haystack.contains(needle)) {
                return true;
            }
        }
        return false;
    }
}
