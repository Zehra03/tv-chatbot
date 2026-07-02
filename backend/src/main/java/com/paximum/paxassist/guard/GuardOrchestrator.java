package com.paximum.paxassist.guard;

import org.springframework.stereotype.Component;

/**
 * Fast-fail entry point: evaluates input against {@link GuardRuleService} and throws before
 * any downstream (LLM/orchestrator) call is made, so a blocked request never reaches the AI layer.
 */
@Component
public class GuardOrchestrator {

    private static final String STANDARD_REJECTION_MESSAGE =
            "İsteğiniz güvenlik politikalarımız gereği işleme alınamamıştır.";

    private final GuardRuleService guardRuleService;

    public GuardOrchestrator(GuardRuleService guardRuleService) {
        this.guardRuleService = guardRuleService;
    }

    public String processInput(String input) {
        GuardResult result = guardRuleService.evaluate(input);
        if (result.isBlocked()) {
            throw new GuardBlockedException(STANDARD_REJECTION_MESSAGE, result.getReason());
        }
        return input;
    }
}
