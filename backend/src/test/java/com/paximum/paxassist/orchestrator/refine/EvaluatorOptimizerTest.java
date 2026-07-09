package com.paximum.paxassist.orchestrator.refine;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EvaluatorOptimizerTest {

    @Mock
    private Generator generator;
    @Mock
    private Evaluator evaluator;

    private EvaluatorOptimizer optimizer(boolean enabled, int maxIterations, int minScore) {
        EvaluatorProperties properties = new EvaluatorProperties();
        properties.setEnabled(enabled);
        properties.setMaxIterations(maxIterations);
        properties.setMinScore(minScore);
        return new EvaluatorOptimizer(properties);
    }

    private static EvaluationResult pass(int score) {
        return new EvaluationResult(true, score, "", List.of());
    }

    private static EvaluationResult fail(int score, String feedback) {
        return new EvaluationResult(false, score, feedback, List.of("UYDURMA"));
    }

    @Test
    void disabled_generatesOnceAndSkipsEvaluation() {
        when(generator.generate(List.of())).thenReturn("candidate");

        String result = optimizer(false, 2, 70).refine(generator, evaluator, "", "FALLBACK");

        assertThat(result).isEqualTo("candidate");
        verify(generator, times(1)).generate(anyList());
        verifyNoInteractions(evaluator);
    }

    @Test
    void acceptsCandidateThatPassesOnFirstRound() {
        when(generator.generate(anyList())).thenReturn("good");
        when(evaluator.evaluate("", "good", "")).thenReturn(pass(90));

        String result = optimizer(true, 2, 70).refine(generator, evaluator, "", "FALLBACK");

        assertThat(result).isEqualTo("good");
        verify(generator, times(1)).generate(anyList());
    }

    @Test
    void feedsFeedbackBackAndAcceptsImprovedCandidate() {
        when(generator.generate(List.of())).thenReturn("bad");
        when(generator.generate(List.of("fiyat uydurma"))).thenReturn("good");
        when(evaluator.evaluate("", "bad", "")).thenReturn(fail(40, "fiyat uydurma"));
        when(evaluator.evaluate("", "good", "")).thenReturn(pass(85));

        String result = optimizer(true, 2, 70).refine(generator, evaluator, "", "FALLBACK");

        assertThat(result).isEqualTo("good");
        verify(generator).generate(List.of("fiyat uydurma")); // feedback threaded into the retry
    }

    @Test
    void returnsSafeFallbackWhenNoCandidatePasses() {
        when(generator.generate(anyList())).thenReturn("bad");
        when(evaluator.evaluate(any(), eq("bad"), any())).thenReturn(fail(10, "kötü"));

        String result = optimizer(true, 2, 70).refine(generator, evaluator, "", "SAFE");

        assertThat(result).isEqualTo("SAFE");
        verify(generator, times(2)).generate(anyList()); // exhausted the iteration budget
    }

    @Test
    void rejectsCandidateBelowMinScoreEvenIfMarkedPass() {
        when(generator.generate(anyList())).thenReturn("meh");
        when(evaluator.evaluate(any(), eq("meh"), any())).thenReturn(pass(50)); // pass=true but score < 70

        String result = optimizer(true, 1, 70).refine(generator, evaluator, "", "SAFE");

        assertThat(result).isEqualTo("SAFE");
    }

    @Test
    void failsOpenToCandidateWhenEvaluatorThrows() {
        when(generator.generate(anyList())).thenReturn("candidate");
        when(evaluator.evaluate(any(), any(), any())).thenThrow(new RuntimeException("critic LLM down"));

        String result = optimizer(true, 2, 70).refine(generator, evaluator, "", "SAFE");

        assertThat(result).isEqualTo("candidate");
    }
}
