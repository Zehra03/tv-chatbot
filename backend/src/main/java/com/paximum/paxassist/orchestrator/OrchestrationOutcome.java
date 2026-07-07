package com.paximum.paxassist.orchestrator;

import com.paximum.paxassist.chat.domain.ChatSession;

/**
 * What {@link ChatOrchestrationService#handle} hands back for one turn: the user-facing
 * {@link OrchestrationResult} plus the live {@link ChatSession} it acted on. The chat layer needs
 * the session too (its accumulated criteria + active domain) to build the frontend response, so
 * both travel together rather than the controller re-loading the session.
 */
public record OrchestrationOutcome(OrchestrationResult result, ChatSession session) {
}
