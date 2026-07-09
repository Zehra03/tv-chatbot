package com.paximum.paxassist.validator;

import com.paximum.paxassist.validator.config.ValidatorProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ValidationOrchestratorTest {

    @Mock
    private ValidatorService validatorService;

    private ValidationOrchestrator orchestrator(boolean enabled, boolean abTestEnabled, int maxRetries,
                                                 boolean cohortCoinFlipResult) {
        ValidatorProperties properties = new ValidatorProperties(enabled, abTestEnabled, maxRetries, 0.0, 256,
                "deepseek-chat", "deepseek");
        return new ValidationOrchestrator(validatorService, properties, () -> cohortCoinFlipResult);
    }

    private ValidatorCallResult callResult(ValidationResult.Verdict verdict) {
        return new ValidatorCallResult(new ValidationResult(verdict, "feedback"), new ValidatorMetrics(5, 10, 2, 12));
    }

    @Test
    void shouldReturnApprovedOutcomeWithNoRetryNeeded() {
        // Given
        when(validatorService.validate(anyString(), anyString(), any()))
                .thenReturn(callResult(ValidationResult.Verdict.APPROVED));
        ValidationOrchestrator orchestrator = orchestrator(true, false, 2, true);

        // When
        ValidationOutcome outcome = orchestrator.validate("soru", "aday", "bağlam", 1);

        // Then
        assertThat(outcome.result().verdict()).isEqualTo(ValidationResult.Verdict.APPROVED);
        assertThat(outcome.retryAllowed()).isFalse();
        assertThat(outcome.cohort()).isEqualTo(Cohort.B_TREATMENT);
    }

    @Test
    void shouldAllowRetryWhenRejectedAndAttemptsRemain() {
        // Given
        when(validatorService.validate(anyString(), anyString(), any()))
                .thenReturn(callResult(ValidationResult.Verdict.REJECTED));
        ValidationOrchestrator orchestrator = orchestrator(true, false, 2, true);

        // When
        ValidationOutcome outcome = orchestrator.validate("soru", "aday", "bağlam", 1);

        // Then
        assertThat(outcome.retryAllowed()).isTrue();
    }

    @Test
    void shouldExhaustRetryWhenRejectedAndAttemptsReachMax() {
        // Given
        when(validatorService.validate(anyString(), anyString(), any()))
                .thenReturn(callResult(ValidationResult.Verdict.REJECTED));
        ValidationOrchestrator orchestrator = orchestrator(true, false, 2, true);

        // When
        ValidationOutcome outcome = orchestrator.validate("soru", "aday", "bağlam", 2);

        // Then
        assertThat(outcome.retryAllowed()).isFalse();
    }

    @Test
    void shouldSkipValidationAndAutoApproveWhenDisabled() {
        // Given
        ValidationOrchestrator orchestrator = orchestrator(false, false, 2, true);

        // When
        ValidationOutcome outcome = orchestrator.validate("soru", "aday", "bağlam", 1);

        // Then
        assertThat(outcome.result().verdict()).isEqualTo(ValidationResult.Verdict.APPROVED);
        assertThat(outcome.cohort()).isEqualTo(Cohort.DISABLED);
        verify(validatorService, never()).validate(any(), any(), any());
    }

    @Test
    void shouldAssignControlCohortAndSkipValidatorWhenAbTestCoinFlipIsFalse() {
        // Given: ab-testing on, coin flip lands on the control group (validator off for this request)
        ValidationOrchestrator orchestrator = orchestrator(true, true, 2, false);

        // When
        ValidationOutcome outcome = orchestrator.validate("soru", "aday", "bağlam", 1);

        // Then
        assertThat(outcome.cohort()).isEqualTo(Cohort.A_CONTROL);
        assertThat(outcome.result().verdict()).isEqualTo(ValidationResult.Verdict.APPROVED);
        verify(validatorService, never()).validate(any(), any(), any());
    }

    @Test
    void shouldAssignTreatmentCohortAndRunValidatorWhenAbTestCoinFlipIsTrue() {
        // Given: ab-testing on, coin flip lands on the treatment group (validator runs)
        when(validatorService.validate(anyString(), anyString(), any()))
                .thenReturn(callResult(ValidationResult.Verdict.APPROVED));
        ValidationOrchestrator orchestrator = orchestrator(true, true, 2, true);

        // When
        ValidationOutcome outcome = orchestrator.validate("soru", "aday", "bağlam", 1);

        // Then
        assertThat(outcome.cohort()).isEqualTo(Cohort.B_TREATMENT);
        verify(validatorService).validate("soru", "aday", "bağlam");
    }

    @Test
    void isRetryAllowed_boundaryTests() {
        ValidationOrchestrator orchestrator = orchestrator(true, false, 2, true);

        assertThat(orchestrator.isRetryAllowed(1)).isTrue();
        assertThat(orchestrator.isRetryAllowed(2)).isFalse();
    }
}
