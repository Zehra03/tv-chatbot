package com.paximum.paxassist.chat.dto;

import jakarta.validation.constraints.NotBlank;
import org.springframework.lang.NonNull;

public record ChatRequest(String sessionId, @NotBlank @NonNull String message) {
}
