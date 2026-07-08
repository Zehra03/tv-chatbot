package com.paximum.paxassist.orchestrator;

import com.paximum.paxassist.ai.IntentType;
import com.paximum.paxassist.ai.SlotCriteria;
import com.paximum.paxassist.chat.domain.ChatSession;

/**
 * Immutable per-turn input handed to an {@link com.paximum.paxassist.orchestrator.intent.IntentHandler}.
 * Carries everything a handler needs and nothing more, so every handler shares the
 * same {@code handle(OrchestrationContext)} signature (Strategy pattern).
 *
 * <p>The {@link ChatSession} inside is intentionally mutable working state — handlers
 * update it (accumulated criteria, last result cards, active domain); the coordinator
 * persists it once at the end of the turn.
 *
 * @param session     the live conversation session (mutable working state)
 * @param userMessage the raw user message for this turn
 * @param intent      the classified intent from the AI layer
 * @param criteria    the slots extracted from this single message (may be null for OTHER)
 */
public record OrchestrationContext(
        ChatSession session,
        String userMessage,
        IntentType intent,
        SlotCriteria criteria
) {
}
