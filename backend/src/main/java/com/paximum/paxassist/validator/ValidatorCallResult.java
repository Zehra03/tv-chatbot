package com.paximum.paxassist.validator;

/** What {@link ValidatorService#validate} returns: the verdict plus the metrics of the call that produced it. */
public record ValidatorCallResult(ValidationResult result, ValidatorMetrics metrics) {
}
