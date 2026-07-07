package com.paximum.paxassist.orchestrator.intent;

import com.paximum.paxassist.ai.IntentType;
import com.paximum.paxassist.orchestrator.OrchestrationContext;
import com.paximum.paxassist.orchestrator.OrchestrationResult;

/**
 * Strategy contract: one implementation per {@link IntentType}. Adding a new intent
 * means adding a new bean that implements this interface — the coordinator and the
 * {@link IntentRouter} never change (Open/Closed Principle). Spring auto-collects
 * every implementation into a {@code List<IntentHandler>}.
 */
public interface IntentHandler {

    /** @return true if this handler is responsible for the given intent. */
    boolean supports(IntentType intent);

    /** Runs the intent-specific logic and returns the user-facing outcome. */
    OrchestrationResult handle(OrchestrationContext context);
}
