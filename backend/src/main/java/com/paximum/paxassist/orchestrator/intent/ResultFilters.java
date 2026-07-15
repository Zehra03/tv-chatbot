package com.paximum.paxassist.orchestrator.intent;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
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

    static List<Object> applyStars(List<Object> cards, Integer minStars, Integer maxStars) {
        if (cards == null || cards.isEmpty() || (minStars == null && maxStars == null)) {
            return cards;
        }
        return cards.stream()
                .filter(card -> {
                    Integer stars = ProductCards.starsOf(card);
                    if (stars == null) return true; // keep if stars unknown
                    boolean minOk = minStars == null || stars >= minStars;
                    boolean maxOk = maxStars == null || stars <= maxStars;
                    return minOk && maxOk;
                })
                .collect(Collectors.toList());
    }

    static List<Object> applyLimit(List<Object> cards, Integer limit) {
        if (cards == null || cards.isEmpty() || limit == null || limit <= 0) {
            return cards;
        }
        return cards.stream().limit(limit).collect(Collectors.toList());
    }

    static List<Object> applySort(List<Object> cards, String sortBy) {
        if (cards == null || cards.isEmpty()) {
            return cards;
        }
        String effectiveSortBy = (sortBy != null && !sortBy.isBlank()) ? sortBy : "price_asc";
        Comparator<Object> comparator = comparatorFor(effectiveSortBy);
        if (comparator == null) {
            return cards;
        }
        List<Object> sorted = new java.util.ArrayList<>(cards);
        sorted.sort(comparator);
        return sorted;
    }

    private static Comparator<Object> comparatorFor(String sortBy) {
        return switch (sortBy) {
            case "price_asc" ->
                    Comparator.comparing(ProductCards::priceOf, Comparator.nullsLast(Comparator.naturalOrder()));
            case "price_desc" ->
                    Comparator.comparing(ProductCards::priceOf, Comparator.nullsLast(Comparator.reverseOrder()));
            case "stars_desc" ->
                    Comparator.comparing(ProductCards::starsOf, Comparator.nullsLast(Comparator.reverseOrder()));
            default -> null;
        };
    }

    /**
     * Keep hotel cards that confirm ALL requested {@link HotelFeature}s against their REAL provider
     * data ({@link HotelProduct#features()} = TourVisio facilities ∪ themes). Unlike
     * {@link #applyBoardType}, an empty result IS meaningful here: for a hard constraint like
     * "denize sıfır" we surface only confirmed matches rather than pretending every hotel qualifies,
     * and the caller words the "none matched" case honestly. Non-hotel cards are left untouched, and
     * unrecognised feature keys are ignored (nothing to evaluate → no filtering).
     */
    static List<Object> applyFeatures(List<Object> cards, List<String> requested) {
        if (requested == null || requested.isEmpty() || cards == null || cards.isEmpty()) {
            return cards;
        }
        List<HotelFeature> features = requested.stream()
                .map(HotelFeature::fromKey)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        if (features.isEmpty()) {
            return cards;
        }
        return cards.stream()
                .filter(card -> matchesAllFeatures(card, features))
                .collect(Collectors.toList());
    }

    /** Human Turkish labels for the recognised requested features, e.g. "denize sıfır, havuzlu". */
    static String describeFeatures(List<String> requested) {
        if (requested == null || requested.isEmpty()) {
            return "";
        }
        return requested.stream()
                .map(HotelFeature::fromKey)
                .filter(Objects::nonNull)
                .map(HotelFeature::label)
                .distinct()
                .collect(Collectors.joining(", "));
    }

    private static boolean matchesAllFeatures(Object card, List<HotelFeature> features) {
        if (!(card instanceof HotelProduct hotel)) {
            return true; // features apply to hotels only; leave other cards untouched
        }
        String blob = String.join(" | ", hotel.features()).toLowerCase(Locale.ROOT);
        for (HotelFeature feature : features) {
            if (!feature.matches(blob)) {
                return false;
            }
        }
        return true;
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
            case "AI", "ALL INCLUSIVE", "ALL-INCLUSIVE", "HERŞEY DAHIL", "HERŞEY DAHİL", "HER ŞEY DAHİL" -> containsAny(b, "herşey", "her şey", "all inclusive", "ultra");
            case "HB", "HALF BOARD", "YARIM PANSIYON", "YARIM PANSİYON" -> containsAny(b, "yarım", "half");
            case "BB", "BED & BREAKFAST", "BED AND BREAKFAST", "ODA KAHVALTI" -> containsAny(b, "kahvaltı", "breakfast", "bed & breakfast", "bed and breakfast");
            // Room-only: check AFTER breakfast so "Oda Kahvaltı" (BB) is not mistaken for RO.
            case "RO", "ROOM ONLY", "SADECE ODA" -> !containsAny(b, "kahvaltı", "breakfast")
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
