package com.paximum.paxassist.chat.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.paximum.paxassist.auth.security.UserPrincipal;
import com.paximum.paxassist.chat.dto.ChatRequest;
import com.paximum.paxassist.chat.dto.ChatSessionDto;
import com.paximum.paxassist.chat.dto.ChatSessionSummaryDto;
import com.paximum.paxassist.chat.dto.SendMessageResponseDto;
import com.paximum.paxassist.chat.service.ChatResponseAssembler;
import com.paximum.paxassist.chat.service.ChatSessionQueryService;
import com.paximum.paxassist.chat.service.ChatSessionStore;
import com.paximum.paxassist.orchestrator.ChatOrchestrationService;
import com.paximum.paxassist.orchestrator.OrchestrationOutcome;

import jakarta.validation.Valid;

/**
 * Thin HTTP boundary for the chat feature — no business logic lives here (project guardrail).
 * POST delegates the whole turn to the orchestrator, then the assembler shapes it to the frontend
 * contract ({@code frontend/src/api/chatApi.ts}); GET/DELETE expose per-user session state. Every
 * endpoint is scoped to the authenticated principal (path-based RBAC lives in Auth's SecurityConfig).
 */
@RestController
@RequestMapping("/api/v1/chat")
public class ChatController {

    private final ChatOrchestrationService orchestrationService;
    private final ChatResponseAssembler responseAssembler;
    private final ChatSessionQueryService sessionQueryService;
    private final ChatSessionStore sessionStore;

    public ChatController(ChatOrchestrationService orchestrationService,
                          ChatResponseAssembler responseAssembler,
                          ChatSessionQueryService sessionQueryService,
                          ChatSessionStore sessionStore) {
        this.orchestrationService = orchestrationService;
        this.responseAssembler = responseAssembler;
        this.sessionQueryService = sessionQueryService;
        this.sessionStore = sessionStore;
    }

    @PostMapping
    public SendMessageResponseDto chat(@RequestBody @Valid ChatRequest request,
                                       @AuthenticationPrincipal UserPrincipal principal) {
        OrchestrationOutcome outcome =
                orchestrationService.handle(request.sessionId(), request.message(), principal.getId());
        return responseAssembler.toResponse(outcome.result(), outcome.session());
    }

    @GetMapping("/sessions")
    public List<ChatSessionSummaryDto> listSessions(@AuthenticationPrincipal UserPrincipal principal) {
        return sessionQueryService.listSummaries(principal.getId());
    }

    @GetMapping("/{sessionId}")
    public ResponseEntity<ChatSessionDto> getSession(@PathVariable String sessionId,
                                                     @AuthenticationPrincipal UserPrincipal principal) {
        return sessionQueryService.getSession(sessionId, principal.getId())
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{sessionId}")
    public ResponseEntity<Void> deleteSession(@PathVariable String sessionId,
                                              @AuthenticationPrincipal UserPrincipal principal) {
        return sessionStore.delete(sessionId, principal.getId())
                ? ResponseEntity.noContent().build()
                : ResponseEntity.notFound().build();
    }
}
