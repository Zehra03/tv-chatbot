package com.paximum.paxassist.guard;

import org.springframework.stereotype.Component;

/**
 * Fast-fail entry point and single facade for every guard rule, so callers depend only on the guard
 * module. It combines the stateless per-message content check ({@link GuardRuleService}) with the
 * stateful per-user out-of-scope abuse block ({@link OutOfScopeGuard}); all throw
 * {@link GuardBlockedException} before any downstream (LLM/orchestrator) call, so a blocked request
 * never reaches the AI layer.
 */
@Component
public class GuardOrchestrator {

    private static final String STANDARD_REJECTION_MESSAGE =
            "İsteğiniz güvenlik politikalarımız gereği işleme alınamamıştır.";

    private final GuardRuleService guardRuleService;
    private final OutOfScopeGuard outOfScopeGuard;

    public GuardOrchestrator(GuardRuleService guardRuleService, OutOfScopeGuard outOfScopeGuard) {
        this.guardRuleService = guardRuleService;
        this.outOfScopeGuard = outOfScopeGuard;
    }

    public String processInput(String input) {
        GuardResult result = guardRuleService.evaluate(input);
        if (result.isBlocked()) {
            throw new GuardBlockedException(STANDARD_REJECTION_MESSAGE, result.getReason());
        }
        return input;
    }

    /** Rejects a user currently under a temporary out-of-scope block, before any LLM call. */
    public void assertNotBlocked(Long userId) {
        outOfScopeGuard.assertNotBlocked(userId);
    }

    /**
     * Records the current turn's classification for out-of-scope abuse tracking. Throws
     * {@link GuardBlockedException} on the turn a user crosses the consecutive-OTHER threshold.
     */
    public void registerOutOfScope(Long userId, boolean isOutOfScope) {
        outOfScopeGuard.registerOutOfScope(userId, isOutOfScope);
    }
}
