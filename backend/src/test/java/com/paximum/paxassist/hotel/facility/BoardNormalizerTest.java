package com.paximum.paxassist.hotel.facility;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * Pure unit tests for {@link BoardNormalizer} — collapsing TourVisio's unreliable free-text
 * {@code boardName} into the detail model's stable codes. No Spring context / services needed.
 */
class BoardNormalizerTest {

    @Test
    void collapsesAllInclusiveVariants() {
        assertEquals("ALL_INCLUSIVE", BoardNormalizer.normalize("ALL INCLUSIVE"));
        assertEquals("ALL_INCLUSIVE", BoardNormalizer.normalize("Herşey Dahil"));
        assertEquals("ALL_INCLUSIVE", BoardNormalizer.normalize("Ultra All Inclusive"));
        assertEquals("ALL_INCLUSIVE", BoardNormalizer.normalize("AI")); // standalone token
    }

    @Test
    void mapsHalfFullBoardAndBreakfast() {
        assertEquals("HALF_BOARD", BoardNormalizer.normalize("Yarım Pansiyon"));
        assertEquals("FULL_BOARD", BoardNormalizer.normalize("Full Board"));
        assertEquals("BED_AND_BREAKFAST", BoardNormalizer.normalize("Breakfast included"));
        // Provider's messy "BB TEST XX": the standalone "BB" token wins.
        assertEquals("BED_AND_BREAKFAST", BoardNormalizer.normalize("BB TEST XX"));
        assertEquals("ROOM_ONLY", BoardNormalizer.normalize("Room Only"));
    }

    @Test
    void doesNotMatchCodeInsideAnotherWord() {
        // "abba" contains "bb" only as a substring, not a standalone token → not BED_AND_BREAKFAST.
        assertEquals("Abba Concept", BoardNormalizer.normalize("Abba Concept"));
    }

    @Test
    void nullAndBlankYieldNull() {
        assertNull(BoardNormalizer.normalize(null));
        assertNull(BoardNormalizer.normalize("   "));
    }

    @Test
    void unknownBoardIsPreservedVerbatim() {
        assertEquals("Weird Concept", BoardNormalizer.normalize("  Weird Concept  "));
    }

    @Test
    void normalizeAllDedupesAndSkipsBlanks() {
        List<String> result = BoardNormalizer.normalizeAll(
                Arrays.asList("ALL INCLUSIVE", "ai", "Breakfast included", null, "  "));
        assertEquals(List.of("ALL_INCLUSIVE", "BED_AND_BREAKFAST"), result);
    }

    @Test
    void normalizeAllNullOrEmptyYieldsEmptyList() {
        assertEquals(List.of(), BoardNormalizer.normalizeAll(null));
        assertEquals(List.of(), BoardNormalizer.normalizeAll(List.of()));
    }
}
