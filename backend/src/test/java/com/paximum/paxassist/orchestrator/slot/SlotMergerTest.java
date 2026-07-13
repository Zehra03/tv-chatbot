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
    void bothNull_returnsEmptyNotNull() {
        // A HOTEL/FLIGHT intent with no slots ("otel arıyorum") + empty session → merge must not
        // return null, or the criteria mappers NPE. Returns an all-null empty criteria instead.
        SlotCriteria merged = merger.merge(null, null);
        assertThat(merged).isNotNull();
        assertThat(merged.location()).isNull();
        assertThat(merged.adults()).isNull();
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
    void perDomainBudgetsMergeIndependently() {
        // Hotel budget set first, then a separate flight budget on a later turn: the two per-domain
        // budgets coexist and neither overwrites the other (nor bleeds into the other search).
        SlotCriteria base = slots(Map.of("location", "Antalya", "adults", 2, "hotelMaxPrice", 18000));
        SlotCriteria update = slots(Map.of("flightMaxPrice", 3000));

        SlotCriteria merged = merger.merge(base, update);

        assertThat(merged.location()).isEqualTo("Antalya");    // kept from base
        assertThat(merged.hotelMaxPrice()).isEqualTo(18000);   // kept from base
        assertThat(merged.flightMaxPrice()).isEqualTo(3000);   // added by update
    }

    @Test
    void emptyChildAgesDoesNotOverwriteExistingList() {
        SlotCriteria base = slots(Map.of("childAges", List.of(5, 8)));
        SlotCriteria update = slots(Map.of("childAges", List.of()));

        SlotCriteria merged = merger.merge(base, update);

        assertThat(merged.childAges()).containsExactly(5, 8);
    }

    @Test
    void featuresAddedByUpdateWhileBaseSearchKept() {
        // User adds "havuzlu" mid-search: features arrive on the update, location stays from base.
        SlotCriteria base = slots(Map.of("location", "Antalya", "adults", 2));
        SlotCriteria update = slots(Map.of("features", List.of("POOL")));

        SlotCriteria merged = merger.merge(base, update);

        assertThat(merged.location()).isEqualTo("Antalya");
        assertThat(merged.features()).containsExactly("POOL");
    }

    @Test
    void emptyFeaturesDoesNotOverwriteExistingList() {
        SlotCriteria base = slots(Map.of("features", List.of("SEAFRONT")));
        SlotCriteria update = slots(Map.of("features", List.of()));

        SlotCriteria merged = merger.merge(base, update);

        assertThat(merged.features()).containsExactly("SEAFRONT");
    }

    @Test
    void emptyStringDoesNotOverwriteExistingValue() {
        SlotCriteria base = slots(Map.of("location", "Antalya"));
        SlotCriteria update = slots(Map.of("location", "")); // LLM halüsinasyonu
        
        SlotCriteria merged = merger.merge(base, update);
        
        assertThat(merged.location()).isEqualTo("Antalya");
    }
    @Test
    void explicitCheckOutUpdateDiscardsOldNights() {
        SlotCriteria base = slots(Map.of("checkIn", "2026-07-15", "checkOut", "2026-07-20", "nights", 5));
        SlotCriteria update = slots(Map.of("checkOut", "2026-07-30"));
        
        SlotCriteria merged = merger.merge(base, update);
        
        assertThat(merged.checkIn()).isEqualTo("2026-07-15");
        assertThat(merged.checkOut()).isEqualTo("2026-07-30");
        assertThat(merged.nights()).isNull();
    }

    @Test
    void explicitNightsUpdateDiscardsOldCheckOut() {
        SlotCriteria base = slots(Map.of("checkIn", "2026-07-15", "checkOut", "2026-07-20", "nights", 5));
        SlotCriteria update = slots(Map.of("nights", 10));
        
        SlotCriteria merged = merger.merge(base, update);
        
        assertThat(merged.checkIn()).isEqualTo("2026-07-15");
        assertThat(merged.checkOut()).isNull();
        assertThat(merged.nights()).isEqualTo(10);
    }
    @Test
    void explicitCheckInUpdateDiscardsOldNights() {
        SlotCriteria base = slots(Map.of("checkIn", "2026-07-15", "checkOut", "2026-07-20", "nights", 5));
        SlotCriteria update = slots(Map.of("checkIn", "2026-07-12"));
        
        SlotCriteria merged = merger.merge(base, update);
        
        assertThat(merged.checkIn()).isEqualTo("2026-07-12");
        assertThat(merged.checkOut()).isEqualTo("2026-07-20");
        assertThat(merged.nights()).isNull();
    }
}
