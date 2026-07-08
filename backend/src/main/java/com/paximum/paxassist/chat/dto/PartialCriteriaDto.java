package com.paximum.paxassist.chat.dto;

import java.util.Map;

/**
 * Slot-filling state so far, matching the frontend's {@code PartialCriteria}
 * ({@code frontend/src/types/chat.ts}): an {@code intent} discriminator plus the partially-filled
 * criteria. Keys inside {@code criteria} use the frontend's field names (see
 * {@code chat.service.ChatResponseAssembler}).
 *
 * @param intent   "hotel" | "flight"
 * @param criteria the accumulated (possibly incomplete) criteria
 */
public record PartialCriteriaDto(String intent, Map<String, Object> criteria) {
}
