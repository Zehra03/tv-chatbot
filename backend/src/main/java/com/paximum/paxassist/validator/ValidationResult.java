package com.paximum.paxassist.validator;

/**
 * Structured output the Validator LLM is forced to return. Spring AI's {@code BeanOutputConverter}
 * injects format instructions into the prompt so the model always responds with this exact shape.
 */
public record ValidationResult(Verdict verdict, String feedback) {

    public enum Verdict {
        APPROVED,
        REJECTED
    }
}
