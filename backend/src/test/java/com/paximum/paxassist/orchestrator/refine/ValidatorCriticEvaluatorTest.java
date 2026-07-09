package com.paximum.paxassist.orchestrator.refine;

import com.paximum.paxassist.validator.Cohort;
import com.paximum.paxassist.validator.ValidationOrchestrator;
import com.paximum.paxassist.validator.ValidationOutcome;
import com.paximum.paxassist.validator.ValidationResult;
import com.paximum.paxassist.validator.ValidatorException;
import com.paximum.paxassist.validator.ValidatorMetrics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ValidatorCriticEvaluatorTest {

    private ValidationOrchestrator validationOrchestrator;
    private ValidatorCriticEvaluator evaluator;

    @BeforeEach
    void setUp() {
        validationOrchestrator = mock(ValidationOrchestrator.class);
        evaluator = new ValidatorCriticEvaluator(validationOrchestrator);
    }

    private ValidationOutcome outcome(ValidationResult.Verdict verdict, String feedback) {
        return new ValidationOutcome(new ValidationResult(verdict, feedback), false,
                Cohort.B_TREATMENT, ValidatorMetrics.none());
    }

    @Test
    void shouldMapApprovedVerdictToPassingResult() {
        // Given
        when(validationOrchestrator.validate(anyString(), anyString(), anyString(), anyInt()))
                .thenReturn(outcome(ValidationResult.Verdict.APPROVED, "ok"));

        // When
        EvaluationResult result = evaluator.evaluate("soru", "aday yanıt", "bağlam");

        // Then
        assertThat(result.pass()).isTrue();
        assertThat(result.score()).isEqualTo(100);
        assertThat(result.feedback()).isEmpty();
        assertThat(result.violations()).isEmpty();
    }

    @Test
    void shouldMapRejectedVerdictToFailingResultWithFeedback() {
        // Given
        when(validationOrchestrator.validate(anyString(), anyString(), anyString(), anyInt()))
                .thenReturn(outcome(ValidationResult.Verdict.REJECTED, "Criterion 2 violated: fabricated hotel"));

        // When
        EvaluationResult result = evaluator.evaluate("soru", "aday yanıt", "bağlam");

        // Then
        assertThat(result.pass()).isFalse();
        assertThat(result.score()).isZero();
        assertThat(result.feedback()).isEqualTo("Criterion 2 violated: fabricated hotel");
        assertThat(result.violations()).containsExactly("VALIDATOR");
    }

    @Test
    void shouldForwardUserPromptAndTreatEachCallAsFirstAttempt() {
        // Given
        when(validationOrchestrator.validate(anyString(), anyString(), anyString(), anyInt()))
                .thenReturn(outcome(ValidationResult.Verdict.APPROVED, "ok"));

        // When
        evaluator.evaluate("kullanıcı sorusu", "aday", "bağlam");

        // Then
        verify(validationOrchestrator).validate("kullanıcı sorusu", "aday", "bağlam", 1);
    }

    @Test
    void shouldPassNullUserPromptAsEmptyString() {
        // Given
        when(validationOrchestrator.validate(anyString(), anyString(), anyString(), anyInt()))
                .thenReturn(outcome(ValidationResult.Verdict.APPROVED, "ok"));

        // When — two-arg interface path used by callers that have no user message
        evaluator.evaluate("aday", "bağlam");

        // Then
        verify(validationOrchestrator).validate("", "aday", "bağlam", 1);
    }

    @Test
    void shouldPropagateValidatorOutageSoOptimizerFailsOpen() {
        // Given — EvaluatorOptimizer owns the fail-open policy, the adapter must not swallow errors
        when(validationOrchestrator.validate(anyString(), anyString(), anyString(), anyInt()))
                .thenThrow(new ValidatorException(ValidatorException.Code.UNAVAILABLE, "down"));

        // When / Then
        assertThatThrownBy(() -> evaluator.evaluate("soru", "aday", "bağlam"))
                .isInstanceOf(ValidatorException.class);
    }
}
