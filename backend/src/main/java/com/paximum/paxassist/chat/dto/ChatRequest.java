package com.paximum.paxassist.chat.dto;

import jakarta.validation.constraints.NotBlank;

public record ChatRequest(String sessionId, @NotBlank String message) {
}
