package com.paximum.paxassist.validator;

/**
 * Per-call performance data for the Validator's own LLM invocation. Shaped so it can sit next to the
 * main (Gemini) LLM's usage numbers for a like-for-like cost/latency comparison during the A/B test.
 */
public record ValidatorMetrics(long latencyMs, int promptTokens, int completionTokens, int totalTokens) {

    private static final ValidatorMetrics NONE = new ValidatorMetrics(0, 0, 0, 0);

    /** Used when the validator never actually called the LLM (kill switch or A/B control group). */
    public static ValidatorMetrics none() {
        return NONE;
    }
}
