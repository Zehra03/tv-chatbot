package com.paximum.paxassist.orchestrator.refine;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Type-safe binding for {@code app.orchestrator.evaluator.*}. Gates the evaluator-optimizer
 * loop so it can be turned off (single generation, no critic) and tuned without code changes.
 */
@ConfigurationProperties(prefix = "app.orchestrator.evaluator")
public class EvaluatorProperties {

    /** When false, the loop is bypassed: the generator runs once and its output is returned as-is. */
    private boolean enabled = true;

    /** Upper bound on generate→evaluate rounds. Guarantees the loop always terminates. */
    private int maxIterations = 2;

    /** A candidate must reach at least this score (0–100) AND pass to be accepted. */
    private int minScore = 70;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getMaxIterations() {
        return maxIterations;
    }

    public void setMaxIterations(int maxIterations) {
        this.maxIterations = maxIterations;
    }

    public int getMinScore() {
        return minScore;
    }

    public void setMinScore(int minScore) {
        this.minScore = minScore;
    }
}
