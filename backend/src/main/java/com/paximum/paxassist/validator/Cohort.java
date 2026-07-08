package com.paximum.paxassist.validator;

/**
 * Tags which A/B path a request took, so logs/metrics can be sliced by cohort during the
 * cost/benefit test (Validator on vs. off) without needing any persisted state.
 */
public enum Cohort {
    /** The module-wide kill switch ({@code app.validator.enabled=false}) — nobody gets validated. */
    DISABLED,
    /** A/B control group: validator skipped, answer auto-approved. */
    A_CONTROL,
    /** A/B treatment group (or normal operation when A/B testing is off): validator actually runs. */
    B_TREATMENT
}
