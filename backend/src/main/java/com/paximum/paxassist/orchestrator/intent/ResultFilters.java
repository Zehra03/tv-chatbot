package com.paximum.paxassist.orchestrator.intent;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.Collectors;

import com.paximum.paxassist.flight.domain.FlightProduct;
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

    /**
     * Keep flight cards that match the direct/layover preference.
     * Hotels are unaffected.
     */
    static List<Object> applyDirectFlight(List<Object> cards, Boolean directFlight) {
        if (directFlight == null || cards == null || cards.isEmpty()) {
            return cards;
        }

        return cards.stream()
                .filter(c -> {
                    if (!(c instanceof FlightProduct f)) {
                        return true; // only filter flights
                    }
                    if (directFlight) {
                        return f.getStops() == 0;
                    } else {
                        return f.getStops() > 0;
                    }
                })
                .collect(Collectors.toList());
    }

    /**
     * Zone the flights' {@code departTime} instants are read in when bucketing by time of day. Fixed to
     * the app's TourVisio default so "sabah/öğlen/akşam" means the local hour printed on the ticket
     * rather than a UTC coincidence. (This is a Turkey-centric product; see {@code tourvisio.timezone}.)
     */
    private static final ZoneId FLIGHT_ZONE = ZoneId.of("Europe/Istanbul");

    /**
     * Keep flight cards whose departure time (local, {@link #FLIGHT_ZONE}) falls in the requested
     * window. The spec is EITHER a time-of-day bucket ("morning"/"afternoon"/"evening"/"night", from
     * "sabah/öğlen/akşam/gece") OR an explicit 24h clock range the user gave in numbers:
     * {@code "HH:mm-HH:mm"}, or open-ended {@code "HH:mm-"} (at or after) / {@code "-HH:mm"} (at or
     * before). Hotels and non-flight cards are unaffected; an unrecognised spec leaves the list
     * untouched. A flight with no departure time is dropped while a window is requested — it cannot be
     * shown as satisfying a filter it can't be evaluated against.
     */
    static List<Object> applyDepartTimeRange(List<Object> cards, String departTimeRange) {
        if (departTimeRange == null || departTimeRange.isBlank() || cards == null || cards.isEmpty()) {
            return cards;
        }
        DepartWindow window = parseWindow(departTimeRange.trim().toLowerCase(Locale.ROOT));
        if (window == null) {
            return cards; // unrecognised spec → no filtering
        }
        return cards.stream()
                .filter(c -> {
                    if (!(c instanceof FlightProduct f)) {
                        return true; // only filter flights
                    }
                    if (f.getDepartTime() == null) {
                        return false;
                    }
                    return window.matches(f.getDepartTime().atZone(FLIGHT_ZONE).toLocalTime());
                })
                .collect(Collectors.toList());
    }

    /**
     * A departure-time window. Bounds may be open ({@code null}). {@code upperExclusive} distinguishes a
     * bucket's half-open hour range from an explicit inclusive range; {@code wraps} covers windows that
     * cross midnight (night 21:00→06:00, or an explicit "22:00-02:00").
     */
    private record DepartWindow(LocalTime from, LocalTime to, boolean upperExclusive, boolean wraps) {
        boolean matches(LocalTime t) {
            boolean afterFrom = from == null || !t.isBefore(from);
            boolean beforeTo = to == null || (upperExclusive ? t.isBefore(to) : !t.isAfter(to));
            return wraps ? (afterFrom || beforeTo) : (afterFrom && beforeTo);
        }
    }

    private static DepartWindow parseWindow(String spec) {
        switch (spec) {
            case "morning":   return new DepartWindow(LocalTime.of(6, 0), LocalTime.of(12, 0), true, false);
            case "afternoon": return new DepartWindow(LocalTime.of(12, 0), LocalTime.of(17, 0), true, false);
            case "evening":   return new DepartWindow(LocalTime.of(17, 0), LocalTime.of(21, 0), true, false);
            case "night":     return new DepartWindow(LocalTime.of(21, 0), LocalTime.of(6, 0), true, true);
            default:          break;
        }
        int dash = spec.indexOf('-');
        if (dash < 0) {
            return null; // neither a bucket nor a range
        }
        LocalTime from = parseClock(spec.substring(0, dash));
        LocalTime to = parseClock(spec.substring(dash + 1));
        if (from == null && to == null) {
            return null;
        }
        boolean wraps = from != null && to != null && from.isAfter(to);
        return new DepartWindow(from, to, false, wraps); // explicit bounds are inclusive
    }

    /** Parses "HH:mm", "H:mm" or a bare hour "HH"; null when blank or out of range. */
    private static LocalTime parseClock(String raw) {
        String s = raw.trim();
        if (s.isEmpty()) {
            return null;
        }
        try {
            int hour;
            int minute = 0;
            int colon = s.indexOf(':');
            if (colon >= 0) {
                hour = Integer.parseInt(s.substring(0, colon).trim());
                minute = Integer.parseInt(s.substring(colon + 1).trim());
            } else {
                hour = Integer.parseInt(s);
            }
            if (hour < 0 || hour > 23 || minute < 0 || minute > 59) {
                return null;
            }
            return LocalTime.of(hour, minute);
        } catch (NumberFormatException e) {
            return null;
        }
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
