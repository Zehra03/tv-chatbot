package com.paximum.paxassist.validator;

/**
 * What {@link ValidationOrchestrator} hands back to a caller: the verdict, whether another
 * feedback-retry cycle is still allowed, which A/B cohort the request fell into, and the call metrics
 * (zeroed out when the validator never actually ran).
 */
public record ValidationOutcome(ValidationResult result, boolean retryAllowed, Cohort cohort, ValidatorMetrics metrics) {

    private static final String AUTO_APPROVE_FEEDBACK =
            "Validator bu istek için çalıştırılmadı (devre dışı veya A/B kontrol grubu) - otomatik onay.";

    /** Used for the kill switch and the A/B control group: no LLM call happens, no retry is offered. */
    public static ValidationOutcome autoApproved(Cohort cohort) {
        return new ValidationOutcome(
                new ValidationResult(ValidationResult.Verdict.APPROVED, AUTO_APPROVE_FEEDBACK),
                false,
                cohort,
                ValidatorMetrics.none());
    }
}
