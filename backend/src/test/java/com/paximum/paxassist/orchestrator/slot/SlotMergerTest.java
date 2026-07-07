package com.paximum.paxassist.orchestrator.slot;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

import com.paximum.paxassist.ai.SlotCriteria;

import static org.assertj.core.api.Assertions.assertThat;

class SlotMergerTest {

    private final SlotMerger merger = new SlotMerger();
    private final ObjectMapper objectMapper = new ObjectMapper();

    private SlotCriteria slots(Map<String, Object> fields) {
        return objectMapper.convertValue(fields, SlotCriteria.class);
    }

    @Test
    void nullUpdate_returnsBaseUnchanged() {
        SlotCriteria base = slots(Map.of("location", "Antalya"));
        assertThat(merger.merge(base, null)).isSameAs(base);
    }

    @Test
    void nullBase_returnsUpdate() {
        SlotCriteria update = slots(Map.of("location", "Antalya"));
        assertThat(merger.merge(null, update)).isSameAs(update);
    }

    @Test
    void updateNonNullWins_baseKeptOtherwise() {
        SlotCriteria base = slots(Map.of("location", "Antalya", "adults", 2));
        SlotCriteria update = slots(Map.of("checkIn", "2026-08-01", "adults", 3));

        SlotCriteria merged = merger.merge(base, update);

        assertThat(merged.location()).isEqualTo("Antalya");     // kept from base
        assertThat(merged.checkIn()).isEqualTo("2026-08-01");   // added by update
        assertThat(merged.adults()).isEqualTo(3);               // overwritten by update
    }

    @Test
    void emptyChildAgesDoesNotOverwriteExistingList() {
        SlotCriteria base = slots(Map.of("childAges", List.of(5, 8)));
        SlotCriteria update = slots(Map.of("childAges", List.of()));

        SlotCriteria merged = merger.merge(base, update);

        assertThat(merged.childAges()).containsExactly(5, 8);
    }
}
