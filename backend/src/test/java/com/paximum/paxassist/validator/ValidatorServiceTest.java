package com.paximum.paxassist.validator;

import com.paximum.paxassist.validator.config.ValidatorProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.ollama.api.OllamaOptions;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ValidatorServiceTest {

    private ChatClient chatClient;
    private ChatClient.ChatClientRequestSpec requestSpec;
    private ChatClient.CallResponseSpec callResponseSpec;
    private ValidatorProperties properties;
    private ValidatorService service;

    @BeforeEach
    void setUp() {
        chatClient = mock(ChatClient.class);
        requestSpec = mock(ChatClient.ChatClientRequestSpec.class);
        callResponseSpec = mock(ChatClient.CallResponseSpec.class);

        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.system(anyString())).thenReturn(requestSpec);
        when(requestSpec.user(anyString())).thenReturn(requestSpec);
        when(requestSpec.options(any())).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(callResponseSpec);

        properties = new ValidatorProperties(true, false, 2, 0.0, 256, "qwen2.5:3b");
        service = new ValidatorService(chatClient, properties);
    }

    private ChatResponse response(String content, int promptTokens, int completionTokens) {
        Generation generation = new Generation(new AssistantMessage(content));
        ChatResponseMetadata metadata = ChatResponseMetadata.builder()
                .usage(new DefaultUsage(promptTokens, completionTokens, promptTokens + completionTokens))
                .build();
        return new ChatResponse(List.of(generation), metadata);
    }

    @Test
    void shouldReturnApprovedVerdict() {
        // Given
        when(callResponseSpec.chatResponse())
                .thenReturn(response("{\"verdict\":\"APPROVED\",\"feedback\":\"ok\"}", 100, 20));

        // When
        ValidatorCallResult callResult = service.validate("soru", "aday yanıt", "bağlam");

        // Then
        assertThat(callResult.result().verdict()).isEqualTo(ValidationResult.Verdict.APPROVED);
        assertThat(callResult.result().feedback()).isEqualTo("ok");
    }

    @Test
    void shouldReturnRejectedVerdictWithFeedback() {
        // Given
        when(callResponseSpec.chatResponse()).thenReturn(response(
                "{\"verdict\":\"REJECTED\",\"feedback\":\"Giriş tarihi geçmişte kalıyor\"}", 90, 25));

        // When
        ValidatorCallResult callResult = service.validate("soru", "aday yanıt", "bağlam");

        // Then
        assertThat(callResult.result().verdict()).isEqualTo(ValidationResult.Verdict.REJECTED);
        assertThat(callResult.result().feedback()).isEqualTo("Giriş tarihi geçmişte kalıyor");
    }

    @Test
    void shouldPassConfiguredTemperatureAndMaxTokensToChatClient() {
        // Given
        when(callResponseSpec.chatResponse())
                .thenReturn(response("{\"verdict\":\"APPROVED\",\"feedback\":\"ok\"}", 10, 5));
        var captor = org.mockito.ArgumentCaptor.forClass(OllamaOptions.class);

        // When
        service.validate("soru", "aday yanıt", "bağlam");

        // Then
        verify(requestSpec).options(captor.capture());
        OllamaOptions captured = captor.getValue();
        assertThat(captured.getTemperature()).isEqualTo(properties.temperature());
        assertThat(captured.getNumPredict()).isEqualTo(properties.maxTokens());
    }

    @Test
    void shouldCaptureLatencyAndTokenUsageMetrics() {
        // Given
        when(callResponseSpec.chatResponse())
                .thenReturn(response("{\"verdict\":\"APPROVED\",\"feedback\":\"ok\"}", 123, 45));

        // When
        ValidatorCallResult callResult = service.validate("soru", "aday yanıt", "bağlam");

        // Then
        assertThat(callResult.metrics().promptTokens()).isEqualTo(123);
        assertThat(callResult.metrics().completionTokens()).isEqualTo(45);
        assertThat(callResult.metrics().totalTokens()).isEqualTo(168);
        assertThat(callResult.metrics().latencyMs()).isGreaterThanOrEqualTo(0);
    }

    @Test
    void shouldFailClosedWhenValidatorOutputCannotBeParsed() {
        // Given
        when(callResponseSpec.chatResponse()).thenReturn(response("bu json değil", 10, 2));

        // When
        ValidatorCallResult callResult = service.validate("soru", "aday yanıt", "bağlam");

        // Then
        assertThat(callResult.result().verdict()).isEqualTo(ValidationResult.Verdict.REJECTED);
    }

    @Test
    void shouldRetryOnTransientErrorThenSucceed() {
        // Given
        when(callResponseSpec.chatResponse())
                .thenThrow(new RuntimeException("connection refused"))
                .thenReturn(response("{\"verdict\":\"APPROVED\",\"feedback\":\"ok\"}", 10, 5));

        // When
        ValidatorCallResult callResult = service.validate("soru", "aday yanıt", "bağlam");

        // Then
        assertThat(callResult.result().verdict()).isEqualTo(ValidationResult.Verdict.APPROVED);
        verify(callResponseSpec, times(2)).chatResponse();
    }

    @Test
    void shouldThrowValidatorExceptionWhenTransientRetriesExhausted() {
        // Given
        when(callResponseSpec.chatResponse()).thenThrow(new RuntimeException("503 server error"));

        // When / Then
        assertThatThrownBy(() -> service.validate("soru", "aday yanıt", "bağlam"))
                .isInstanceOf(ValidatorException.class)
                .satisfies(e -> assertThat(((ValidatorException) e).getCode())
                        .isEqualTo(ValidatorException.Code.UNAVAILABLE));
        verify(callResponseSpec, times(3)).chatResponse();
    }

    @Test
    void shouldThrowValidatorExceptionWhenChatResponseIsNull() {
        // Given
        when(callResponseSpec.chatResponse()).thenReturn(null);

        // When / Then
        assertThatThrownBy(() -> service.validate("soru", "aday yanıt", "bağlam"))
                .isInstanceOf(ValidatorException.class)
                .satisfies(e -> assertThat(((ValidatorException) e).getCode())
                        .isEqualTo(ValidatorException.Code.UNKNOWN));
    }
}
