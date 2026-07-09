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

    /**
     * Overload that also carries the user's original message, for critics whose rules depend on
     * what the user actually asked (e.g. the Validator module's re-ask and past-date checks).
     * Default implementation ignores it so existing evaluators keep working unchanged.
     */
    default EvaluationResult evaluate(String userPrompt, String candidate, String context) {
        return evaluate(candidate, context);
    }
}
