package com.paximum.paxassist.orchestrator.refine;

/**
 * Scores a candidate output against the project guardrails. Pluggable (Strategy) so the loop
 * doesn't depend on how evaluation is done — {@link GuardrailEvaluator} is the LLM-critic impl.
 */
public interface Evaluator {

    /**
     * @param candidate the generated text to judge
     * @param context   allowed facts the candidate must stay faithful to (e.g. the real result
     *                  cards); pass empty/null for a pure guardrail check with no source facts
     */
    EvaluationResult evaluate(String candidate, String context);
}
