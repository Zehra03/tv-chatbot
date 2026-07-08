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
 */
@Component
public class ValidationOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(ValidationOrchestrator.class);

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

    public ValidationOutcome validate(String question, String candidateAnswer, String groundingContext,
                                       int attemptNumber) {
        if (!validatorProperties.enabled()) {
            log.info("validator.cohort={} action=auto-approve", Cohort.DISABLED);
            return ValidationOutcome.autoApproved(Cohort.DISABLED);
        }

        Cohort cohort = assignCohort();
        if (cohort == Cohort.A_CONTROL) {
            log.info("validator.cohort={} action=auto-approve", Cohort.A_CONTROL);
            return ValidationOutcome.autoApproved(Cohort.A_CONTROL);
        }

        log.info("validator.cohort={} action=validate attempt={}", Cohort.B_TREATMENT, attemptNumber);
        ValidatorCallResult callResult = validatorService.validate(question, candidateAnswer, groundingContext);
        boolean retryAllowed = callResult.result().verdict() == ValidationResult.Verdict.REJECTED
                && isRetryAllowed(attemptNumber);
        return new ValidationOutcome(callResult.result(), retryAllowed, Cohort.B_TREATMENT, callResult.metrics());
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
