package com.paximum.paxassist.orchestrator.refine;

import java.util.List;

/**
 * Produces a candidate output for the evaluator-optimizer loop. On the first round
 * {@code feedbackSoFar} is empty; on later rounds it carries the critic's feedback so the
 * generator can regenerate an improved version. Kept as a functional interface so callers
 * supply a lambda that closes over whatever context they need (e.g. the user message).
 */
@FunctionalInterface
public interface Generator {

    String generate(List<String> feedbackSoFar);
}
