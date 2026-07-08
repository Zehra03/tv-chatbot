package com.paximum.paxassist.orchestrator.refine;

import java.util.List;

/**
 * Structured verdict returned by an {@link Evaluator}. When produced by an LLM critic,
 * Spring AI's BeanOutputConverter derives the JSON schema from this record and forces the
 * model to answer in exactly this shape.
 *
 * @param pass       true only if the candidate satisfies every guardrail
 * @param score      overall quality 0–100 (a candidate must also clear the configured minScore)
 * @param feedback   short Turkish note on how to fix the candidate when it fails; "" when it passes
 * @param violations names of the rules the candidate broke (empty when it passes)
 */
public record EvaluationResult(
        boolean pass,
        int score,
        String feedback,
        List<String> violations
) {
}
