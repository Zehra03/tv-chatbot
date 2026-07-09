package com.paximum.paxassist.orchestrator.refine;

import java.util.List;

import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import com.paximum.paxassist.validator.ValidationOrchestrator;
import com.paximum.paxassist.validator.ValidationOutcome;
import com.paximum.paxassist.validator.ValidationResult;

/**
 * {@link Evaluator} implementation backed by the dedicated Validator module (second-layer LLM,
 * see {@code validator.ValidatorService}) instead of the main chat model judging itself like
 * {@link GuardrailEvaluator} does. Marked {@code @Primary} so the refine loop uses it by default;
 * the Validator module's own kill switch ({@code app.validator.enabled=false}) auto-approves every
 * candidate, which effectively turns the critic off without any bean rewiring.
 *
 * <p>A validator outage ({@code ValidatorException}) is deliberately not caught here —
 * {@link EvaluatorOptimizer} owns the resilience policy and fails open to the candidate, so a
 * validator crash degrades to "no second opinion" rather than blocking the main answer.
 */
@Component
@Primary
public class ValidatorCriticEvaluator implements Evaluator {

    private static final int PASS_SCORE = 100;
    private static final int FAIL_SCORE = 0;

    private final ValidationOrchestrator validationOrchestrator;

    public ValidatorCriticEvaluator(ValidationOrchestrator validationOrchestrator) {
        this.validationOrchestrator = validationOrchestrator;
    }

    @Override
    public EvaluationResult evaluate(String candidate, String context) {
        return evaluate("", candidate, context);
    }

    @Override
    public EvaluationResult evaluate(String userPrompt, String candidate, String context) {
        // The refine loop owns retry counting, so each call is attempt 1 from the validator's view.
        ValidationOutcome outcome = validationOrchestrator.validate(
                userPrompt == null ? "" : userPrompt, candidate, context, 1);

        boolean pass = outcome.result().verdict() == ValidationResult.Verdict.APPROVED;
        return new EvaluationResult(
                pass,
                pass ? PASS_SCORE : FAIL_SCORE,
                pass ? "" : outcome.result().feedback(),
                pass ? List.of() : List.of("VALIDATOR"));
    }
}
