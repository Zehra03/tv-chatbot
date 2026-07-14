package com.paximum.paxassist.guard;

import org.springframework.stereotype.Component;

import com.paximum.paxassist.guard.config.GuardProperties;

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

    /** Polite, fixed text — no LLM call, so an oversized message costs no tokens. */
    static final String TOO_LONG_MESSAGE =
            "Mesajınız çok uzun, lütfen daha kısa bir mesajla tekrar deneyin.";
    private static final String TOO_LONG_REASON = "Message exceeds the configured maximum length";

    private final GuardRuleService guardRuleService;
    private final OutOfScopeGuard outOfScopeGuard;
    private final GuardProperties guardProperties;

    public GuardOrchestrator(GuardRuleService guardRuleService, OutOfScopeGuard outOfScopeGuard,
                             GuardProperties guardProperties) {
        this.guardRuleService = guardRuleService;
        this.outOfScopeGuard = outOfScopeGuard;
        this.guardProperties = guardProperties;
    }

    public String processInput(String input) {
        // Length first: it is O(1), and it keeps a pathologically long body out of the regex rules
        // below. Defense-in-depth — ChatRequest's @Size already rejects an oversized HTTP body, but the
        // guard must hold the same line for any caller that reaches the pipeline another way.
        if (input != null && input.length() > guardProperties.maxMessageLength()) {
            throw new GuardBlockedException(TOO_LONG_MESSAGE, TOO_LONG_REASON);
        }

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
