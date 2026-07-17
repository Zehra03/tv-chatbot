package com.paximum.paxassist.hotel.facility;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Normalizes TourVisio's free-text {@code boardName} into a small stable code set for the detail
 * response ({@code ALL_INCLUSIVE}, {@code FULL_BOARD}, {@code HALF_BOARD}, {@code BED_AND_BREAKFAST},
 * {@code ROOM_ONLY}). Board data is unreliable free text with no fixed enum — the same concept shows
 * up as {@code "ALL INCLUSIVE"}, {@code "Herşey Dahil"}, {@code "ai"}, etc. — so this collapses the
 * common variants by keyword. Anything unrecognized is preserved verbatim (trimmed) rather than
 * dropped, so no board information is invented and none is silently lost.
 *
 * <p>Uses explicit null/blank checks (not exceptions) per the card's handling rules.
 *
 * <p><b>Why these codes differ from the chat filter's {@code AI/HB/BB/RO}.</b> This verbose set
 * ({@code ALL_INCLUSIVE/FULL_BOARD/HALF_BOARD/BED_AND_BREAKFAST/ROOM_ONLY}) is the frontend-facing
 * detail model ({@code HotelFeatureDetails.boardOptions}). The orchestrator's post-search board
 * filter ({@code orchestrator.intent.ResultFilters#applyBoardType}) uses the compact codes the AI
 * Intention layer emits instead. They live in different layers with different consumers (detail DTO
 * vs. chat filter) and are intentionally NOT shared — unifying them would require changing both the
 * AI prompt schema and the frontend contract, so keep them separate.
 */
public final class BoardNormalizer {

    private BoardNormalizer() {
    }

    /**
     * @return the normalized board code, or {@code null} when the input is null/blank. Ordering of the
     *         checks matters: all-inclusive and full/half board are tested before the plain
     *         "breakfast"/"room" checks so a richer board is not mistaken for a lesser one.
     */
    public static String normalize(String boardName) {
        if (boardName == null || boardName.isBlank()) {
            return null;
        }
        String b = boardName.trim().toLowerCase(Locale.ROOT);

        if (containsAny(b, "all inclusive", "all-inclusive", "herşey dahil", "her şey dahil", "hersey dahil", "ultra")
                || hasToken(b, "ai")) {
            return "ALL_INCLUSIVE";
        }
        if (containsAny(b, "full board", "tam pansiyon") || hasToken(b, "fb")) {
            return "FULL_BOARD";
        }
        if (containsAny(b, "half board", "yarım pansiyon", "yarim pansiyon") || hasToken(b, "hb")) {
            return "HALF_BOARD";
        }
        if (containsAny(b, "breakfast", "kahvaltı", "kahvalti", "bed and breakfast", "bed & breakfast", "oda kahvaltı")
                || hasToken(b, "bb")) {
            return "BED_AND_BREAKFAST";
        }
        if (containsAny(b, "room only", "sadece oda") || hasToken(b, "ro")) {
            return "ROOM_ONLY";
        }
        // Unknown board concept — keep the provider's own wording so nothing is fabricated or lost.
        return boardName.trim();
    }

    /**
     * Normalizes a collection of raw board names into distinct codes, preserving first-seen order.
     * Null/blank entries are skipped; an empty or null input yields an empty list.
     */
    public static List<String> normalizeAll(Collection<String> boardNames) {
        if (boardNames == null || boardNames.isEmpty()) {
            return List.of();
        }
        Set<String> codes = new LinkedHashSet<>();
        for (String name : boardNames) {
            String code = normalize(name);
            if (code != null) {
                codes.add(code);
            }
        }
        return new ArrayList<>(codes);
    }

    private static boolean containsAny(String haystack, String... needles) {
        for (String needle : needles) {
            if (haystack.contains(needle)) {
                return true;
            }
        }
        return false;
    }

    /** True when {@code code} appears as a standalone token (so "bb" matches "BB TEST" but not "abba"). */
    private static boolean hasToken(String haystack, String code) {
        for (String token : haystack.split("[^a-z]+")) {
            if (token.equals(code)) {
                return true;
            }
        }
        return false;
    }
}
