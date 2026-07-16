package com.paximum.paxassist.orchestrator.slot;

import java.util.Map;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import com.paximum.paxassist.ai.SlotCriteria;
import com.paximum.paxassist.chat.domain.ChatSession;

/**
 * Bridges the session's untyped {@code accumulatedCriteria} map (aligned with the future
 * {@code chat_sessions.accumulated_criteria} JSONB column) and the typed {@link SlotCriteria}
 * used for merging. On each turn it reads the prior accumulated criteria, merges the newly
 * extracted slots into it (via {@link SlotMerger}), and writes the result back onto the session.
 *
 * <p>Keeping the Jackson conversion here — not in {@link SlotMerger} — lets the merge rule stay
 * pure while this class owns the storage-shape concern (Single Responsibility).
 */
@Component
public class SlotFillingService {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final ObjectMapper objectMapper;
    private final SlotMerger slotMerger;
    private final SlotNormalizer slotNormalizer;

    public SlotFillingService(ObjectMapper objectMapper, SlotMerger slotMerger, SlotNormalizer slotNormalizer) {
        this.objectMapper = objectMapper;
        this.slotMerger = slotMerger;
        this.slotNormalizer = slotNormalizer;
    }

    /**
     * Merges the incoming criteria with the session's accumulated criteria,
     * but does NOT normalize or persist the state. Returns the raw merged result
     * for pre-validation purposes.
     */
    public SlotCriteria peekMerge(ChatSession session, SlotCriteria incoming) {
        SlotCriteria previous = fromMap(session.getAccumulatedCriteria());
        return slotMerger.merge(previous, incoming);
    }

    /**
     * Merges {@code incoming} into the session's accumulated criteria, persists the merged
     * state back onto the session, and returns the merged, typed criteria.
     */
    public SlotCriteria accumulate(ChatSession session, SlotCriteria incoming) {
        SlotCriteria previous = fromMap(session.getAccumulatedCriteria());
        SlotCriteria merged = slotMerger.merge(previous, incoming);
        merged = slotNormalizer.normalize(merged);
        session.setAccumulatedCriteria(toMap(merged));
        return merged;
    }

    private SlotCriteria fromMap(Map<String, Object> map) {
        if (map == null || map.isEmpty()) {
            return null;
        }
        return objectMapper.convertValue(map, SlotCriteria.class);
    }

    private Map<String, Object> toMap(SlotCriteria criteria) {
        return objectMapper.convertValue(criteria, MAP_TYPE);
    }
}
