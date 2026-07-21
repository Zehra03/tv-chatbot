package com.paximum.paxassist.validator;

import com.paximum.paxassist.common.AppConstants;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

public class MaxSearchDateValidator implements ConstraintValidator<MaxSearchDate, LocalDate> {

    @Override
    public boolean isValid(LocalDate dateField, ConstraintValidatorContext context) {
        if (dateField == null) {
            return true; // Let @NotNull handle nulls
        }
        
        long daysBetween = ChronoUnit.DAYS.between(LocalDate.now(), dateField);
        return daysBetween <= AppConstants.MAX_SEARCH_DAYS_AHEAD;
    }
}
