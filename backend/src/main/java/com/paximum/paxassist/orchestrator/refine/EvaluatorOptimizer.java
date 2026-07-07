package com.paximum.paxassist.orchestrator.refine;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Runs a bounded generate → evaluate → refine loop ("Building Effective Agents" evaluator-optimizer).
 * Generic over any {@link Generator} and {@link Evaluator}, so it can guard conversational replies,
 * result summaries, or LLM-phrased questions without knowing their specifics.
 *
 * <p>Safety properties:
 * <ul>
 *   <li><b>Config gate</b> — when disabled, the generator runs once and its output is returned as-is.</li>
 *   <li><b>Bounded</b> — at most {@code maxIterations} rounds, so the loop always terminates.</li>
 *   <li><b>Safe fallback</b> — if no candidate passes within the budget, a safe canned string is
 *       returned; a candidate that failed its last check is never shown to the user.</li>
 *   <li><b>Fail-open on critic outage</b> — if the evaluator itself throws, the current candidate is
 *       accepted (the critic is a second layer; generation already ran under the assistant guardrails).</li>
 * </ul>
 */
@Component
public class EvaluatorOptimizer {

    private static final Logger log = LoggerFactory.getLogger(EvaluatorOptimizer.class);

    private final EvaluatorProperties properties;

    public EvaluatorOptimizer(EvaluatorProperties properties) {
        this.properties = properties;
    }

    /**
     * @param generator    produces a candidate (given accumulated feedback)
     * @param evaluator    judges the candidate against the guardrails
     * @param context      allowed facts passed to the evaluator (may be null/empty)
     * @param safeFallback returned when no candidate passes within the iteration budget
     * @return an accepted candidate, or {@code safeFallback} if none passed
     */
    public String refine(Generator generator, Evaluator evaluator, String context, String safeFallback) {
        if (!properties.isEnabled()) {
            return generator.generate(List.of());
        }

        int maxIterations = Math.max(1, properties.getMaxIterations());
        List<String> feedback = new ArrayList<>();

        for (int round = 1; round <= maxIterations; round++) {
            String candidate = generator.generate(List.copyOf(feedback));

            EvaluationResult result;
            try {
                result = evaluator.evaluate(candidate, context);
            } catch (RuntimeException e) {
                log.warn("Evaluator unavailable on round {}, accepting candidate as-is: {}",
                        round, e.getMessage());
                return candidate;
            }

            if (result.pass() && result.score() >= properties.getMinScore()) {
                return candidate;
            }

            log.debug("Candidate rejected on round {}/{}: score={}, violations={}",
                    round, maxIterations, result.score(), result.violations());
            if (result.feedback() != null && !result.feedback().isBlank()) {
                feedback.add(result.feedback());
            }
        }

        log.info("No candidate passed within {} rounds; returning safe fallback.", maxIterations);
        return safeFallback;
    }
}
