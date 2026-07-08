package com.paximum.paxassist.orchestrator.intent;

import java.util.List;

import org.springframework.stereotype.Component;

import com.paximum.paxassist.ai.IntentType;

/**
 * Strategy registry that picks the {@link IntentHandler} for a given {@link IntentType}.
 * Spring injects EVERY {@code IntentHandler} bean into {@code handlers}, so adding a new intent
 * is just adding a new handler bean — this class never changes (Open/Closed Principle).
 * When no handler matches (or the intent is null), it falls back to {@link FallbackHandler}.
 */
@Component
public class IntentRouter {

    private final List<IntentHandler> handlers;
    private final FallbackHandler fallbackHandler;

    public IntentRouter(List<IntentHandler> handlers, FallbackHandler fallbackHandler) {
        this.handlers = handlers;
        this.fallbackHandler = fallbackHandler;
    }

    public IntentHandler route(IntentType intent) {
        if (intent == null) {
            return fallbackHandler;
        }
        return handlers.stream()
                .filter(handler -> handler.supports(intent))
                .findFirst()
                .orElse(fallbackHandler);
    }
}
