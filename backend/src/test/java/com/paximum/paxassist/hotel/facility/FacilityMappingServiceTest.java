package com.paximum.paxassist.hotel.facility;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Unit tests for {@link FacilityMappingService}, exercised against the real {@code facility-mapping.json}
 * on the classpath. No Spring context needed — the service just needs an {@link ObjectMapper}.
 */
class FacilityMappingServiceTest {

    private FacilityMappingService mapping;

    @BeforeEach
    void setUp() {
        mapping = new FacilityMappingService(new ObjectMapper());
    }

    @Test
    void resolvesGroupByFacilityId() {
        assertEquals(List.of("pool"), mapping.groupsFor(List.of(47, 17), List.of()));
        assertTrue(mapping.groupsFor(List.of(46), List.of()).contains(FacilityMappingService.PET_FRIENDLY));
    }

    @Test
    void kidsPoolBelongsToBothPoolAndKids() {
        List<String> groups = mapping.groupsFor(List.of(36), List.of());
        assertTrue(groups.contains("pool"), "Kids Pool (36) should be in pool");
        assertTrue(groups.contains("kids"), "Kids Pool (36) should also be in kids");
    }

    @Test
    void unknownIdIsUngroupedNotAnError() {
        assertEquals(List.of(), mapping.groupsFor(List.of(999), List.of()));
    }

    @Test
    void resolvesGroupByNameCaseInsensitively() {
        assertEquals(List.of("pool"), mapping.groupsFor(List.of(), List.of("Outdoor Pool")));
        assertEquals(List.of("pool"), mapping.groupsFor(List.of(), List.of("outdoor pool")));
    }

    @Test
    void groupOrderIsDeterministicFollowingTheFile() {
        // pool precedes beach in facility-mapping.json; output must follow that order regardless of input order.
        assertEquals(List.of("pool", "beach"), mapping.groupsFor(List.of(48, 47), List.of()));
    }

    @Test
    void emptyInputYieldsNoGroups() {
        assertEquals(List.of(), mapping.groupsFor(List.of(), List.of()));
        assertEquals(List.of(), mapping.groupsFor(null, null));
    }

    @Test
    void groupsForTextMatchesKeywords() {
        List<String> groups = mapping.groupsForText("evcil hayvanlı havuzlu bir otel");
        assertTrue(groups.contains("pet_friendly"));
        assertTrue(groups.contains("pool"));
        assertFalse(groups.contains("business"));
    }
}
