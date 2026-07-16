package com.paximum.paxassist.validator;

import com.paximum.paxassist.validator.config.ValidatorProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.concurrent.ThreadLocalRandom;
import java.util.function.BooleanSupplier;

/**
 * Stateless entry point for the Validator module — same style as {@code guard.GuardOrchestrator}.
 * Manages the module-wide kill switch, the 50/50 A/B cohort split used for the cost/benefit test, and
 * the feedback-retry ceiling. Callers pass in {@code attemptNumber} themselves; nothing is persisted.
 *
 * <p>Every verdict is written as one {@code validator.feedback} line carrying the caller's
 * {@code traceId} (the chat session id). That is the hook the test catalogue's fail table hangs on:
 * a scenario run records its trace id, and every rejection/feedback for that run is then
 * {@code grep}-able by it — see the fail-record table in {@code docs/chatbot-test-senaryolari.md}.
 *
 * <h2>Scope decision: why only OTHER answers are validated</h2>
 * The single caller is the OTHER-intent fallback, i.e. the only reply the main LLM writes freely.
 * Search summaries and clarifying questions are <b>deterministically generated</b> from real provider
 * results and a fixed question catalogue — no model text, so there is nothing for a critic to catch:
 * a price cannot be fabricated where no price is written by a model, and the wording cannot leak a
 * system prompt it never saw. Sending them through a second LLM would add a call, latency and tokens
 * per turn for no reachable class of defect, and the module's A/B test exists precisely to keep that
 * cost honest. <b>Decision: keep the scope as-is.</b> Revisit if either surface starts composing its
 * text with an LLM (e.g. LLM-phrased clarifying questions, or model-written result summaries) — at
 * that point they gain the same fabrication surface as OTHER and must be validated too.
 */
@Component
public class ValidationOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(ValidationOrchestrator.class);

    /** Trace id used when a caller has no session/correlation id to attach. */
    private static final String NO_TRACE_ID = "-";

    private final ValidatorService validatorService;
    private final ValidatorProperties validatorProperties;
    private final BooleanSupplier cohortCoinFlip;

    @Autowired
    public ValidationOrchestrator(ValidatorService validatorService, ValidatorProperties validatorProperties) {
        this(validatorService, validatorProperties, () -> ThreadLocalRandom.current().nextBoolean());
    }

    /** Package-private: lets tests inject a deterministic coin flip instead of real randomness. */
    ValidationOrchestrator(ValidatorService validatorService, ValidatorProperties validatorProperties,
                           BooleanSupplier cohortCoinFlip) {
        this.validatorService = validatorService;
        this.validatorProperties = validatorProperties;
        this.cohortCoinFlip = cohortCoinFlip;
    }

    /**
     * Untraced overload, for callers that genuinely have no correlation id. Verdicts still log, with
     * {@code traceId=-}: without an id a rejection cannot be tied back to the conversation that produced
     * it, so prefer {@link #validate(String, String, String, String, int)}.
     */
    public ValidationOutcome validate(String question, String candidateAnswer, String groundingContext,
                                       int attemptNumber) {
        return validate(null, question, candidateAnswer, groundingContext, attemptNumber);
    }

    /**
     * @param traceId correlation id for the conversation turn (the chat session id) — stamped on every
     *                {@code validator.feedback} log line so a failing test scenario can be traced to the
     *                exact verdict and feedback that produced it. {@code null}/blank logs as {@code -}.
     */
    public ValidationOutcome validate(String traceId, String question, String candidateAnswer,
                                       String groundingContext, int attemptNumber) {
        String trace = traceOrDefault(traceId);

        if (!validatorProperties.enabled()) {
            log.info("validator.feedback traceId={} cohort={} attempt={} verdict=AUTO_APPROVED action=auto-approve",
                    trace, Cohort.DISABLED, attemptNumber);
            return ValidationOutcome.autoApproved(Cohort.DISABLED);
        }

        Cohort cohort = assignCohort();
        if (cohort == Cohort.A_CONTROL) {
            log.info("validator.feedback traceId={} cohort={} attempt={} verdict=AUTO_APPROVED action=auto-approve",
                    trace, Cohort.A_CONTROL, attemptNumber);
            return ValidationOutcome.autoApproved(Cohort.A_CONTROL);
        }

        ValidatorCallResult callResult = validatorService.validate(question, candidateAnswer, groundingContext);
        boolean retryAllowed = callResult.result().verdict() == ValidationResult.Verdict.REJECTED
                && isRetryAllowed(attemptNumber);

        // One line per verdict, keyed by traceId: the evidence a fail-record row points at.
        log.info("validator.feedback traceId={} cohort={} attempt={} verdict={} retryAllowed={} latencyMs={} "
                        + "action={} feedback=\"{}\"",
                trace, Cohort.B_TREATMENT, attemptNumber, callResult.result().verdict(), retryAllowed,
                callResult.metrics().latencyMs(), action(callResult.result().verdict(), retryAllowed),
                singleLine(callResult.result().feedback()));

        return new ValidationOutcome(callResult.result(), retryAllowed, Cohort.B_TREATMENT, callResult.metrics());
    }

    /** What the caller will do with this verdict — makes the log readable without the handler's source. */
    private String action(ValidationResult.Verdict verdict, boolean retryAllowed) {
        if (verdict == ValidationResult.Verdict.APPROVED) {
            return "accept";
        }
        return retryAllowed ? "regenerate" : "safe-fallback";
    }

    private String traceOrDefault(String traceId) {
        return (traceId == null || traceId.isBlank()) ? NO_TRACE_ID : traceId;
    }

    /** Feedback is LLM-generated free text; collapse line breaks so one verdict stays one log line. */
    private String singleLine(String text) {
        return text == null ? "" : text.replaceAll("\\R+", " ").strip();
    }

    /** {@code attemptNumber} is 1-based; a retry is allowed while it's still below {@code max-retries}. */
    public boolean isRetryAllowed(int attemptNumber) {
        return attemptNumber < validatorProperties.maxRetries();
    }

    private Cohort assignCohort() {
        if (!validatorProperties.abTestEnabled()) {
            return Cohort.B_TREATMENT;
        }
        return cohortCoinFlip.getAsBoolean() ? Cohort.B_TREATMENT : Cohort.A_CONTROL;
    }
}
