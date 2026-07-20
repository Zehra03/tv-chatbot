package com.paximum.paxassist.validator;

import com.paximum.paxassist.common.AppConstants;
import jakarta.validation.ConstraintValidatorContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(MockitoExtension.class)
class MaxSearchDateValidatorTest {

    private MaxSearchDateValidator validator;

    @Mock
    private ConstraintValidatorContext context;

    @BeforeEach
    void setUp() {
        validator = new MaxSearchDateValidator();
        validator.initialize(null); // Assuming no specific initialization is needed
    }

    @Test
    void isValid_ShouldReturnTrue_WhenDateIsNull() {
        assertTrue(validator.isValid(null, context));
    }

    @Test
    void isValid_ShouldReturnTrue_WhenDateIsToday() {
        LocalDate today = LocalDate.now();
        assertTrue(validator.isValid(today, context));
    }

    @Test
    void isValid_ShouldReturnTrue_WhenDateIsExactlyMaxDaysAhead() {
        LocalDate maxDate = LocalDate.now().plusDays(AppConstants.MAX_SEARCH_DAYS_AHEAD);
        assertTrue(validator.isValid(maxDate, context));
    }

    @Test
    void isValid_ShouldReturnFalse_WhenDateIsBeyondMaxDaysAhead() {
        LocalDate beyondMaxDate = LocalDate.now().plusDays(AppConstants.MAX_SEARCH_DAYS_AHEAD + 1);
        assertFalse(validator.isValid(beyondMaxDate, context));
    }
    
    @Test
    void isValid_ShouldReturnTrue_WhenDateIsInThePast() {
        LocalDate pastDate = LocalDate.now().minusDays(1);
        // Past dates are technically within the "ahead" limit from this validator's perspective.
        // @FutureOrPresent handles past dates, so this one just checks the upper bound.
        assertTrue(validator.isValid(pastDate, context));
    }
}
