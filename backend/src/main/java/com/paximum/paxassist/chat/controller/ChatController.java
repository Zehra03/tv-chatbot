package com.paximum.paxassist.chat.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.paximum.paxassist.auth.security.UserPrincipal;
import com.paximum.paxassist.chat.domain.ChatCaller;
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
 * contract ({@code frontend/src/api/chatApi.ts}); GET/DELETE expose per-owner session state.
 *
 * <p>Chat is open to guests (SecurityConfig {@code permitAll}), so a caller is either a logged-in
 * user ({@code @AuthenticationPrincipal}) or an anonymous guest carrying an opaque {@code X-Guest-Id}
 * header — resolved into a {@link ChatCaller} that scopes every session operation. A request with
 * neither identity is rejected up front.
 */
@RestController
@RequestMapping("/api/v1/chat")
public class ChatController {

    /** Bound by the {@code guest_token varchar(64)} column; longer values are rejected, not truncated. */
    private static final int MAX_GUEST_ID_LENGTH = 64;
    private static final String GUEST_ID_HEADER = "X-Guest-Id";

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
                                       @AuthenticationPrincipal UserPrincipal principal,
                                       @RequestHeader(value = GUEST_ID_HEADER, required = false) String guestId) {
        ChatCaller caller = resolveCaller(principal, guestId);
        OrchestrationOutcome outcome =
                orchestrationService.handle(request.sessionId(), request.message(), caller);
        return responseAssembler.toResponse(outcome.result(), outcome.session());
    }

    @GetMapping("/sessions")
    public List<ChatSessionSummaryDto> listSessions(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestHeader(value = GUEST_ID_HEADER, required = false) String guestId) {
        return sessionQueryService.listSummaries(resolveCaller(principal, guestId));
    }

    @GetMapping("/{sessionId}")
    public ResponseEntity<ChatSessionDto> getSession(
            @PathVariable String sessionId,
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestHeader(value = GUEST_ID_HEADER, required = false) String guestId) {
        return sessionQueryService.getSession(sessionId, resolveCaller(principal, guestId))
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{sessionId}")
    public ResponseEntity<Void> deleteSession(
            @PathVariable String sessionId,
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestHeader(value = GUEST_ID_HEADER, required = false) String guestId) {
        return sessionStore.delete(sessionId, resolveCaller(principal, guestId))
                ? ResponseEntity.noContent().build()
                : ResponseEntity.notFound().build();
    }

    /**
     * A logged-in user wins over any guest header; otherwise the request must carry a usable
     * {@code X-Guest-Id}. A request with neither identity is unauthorized (the frontend always sends
     * one), and an over-length guest id is a bad request (would overflow the column).
     */
    private ChatCaller resolveCaller(UserPrincipal principal, String guestId) {
        if (principal != null) {
            return ChatCaller.authenticated(principal.getId());
        }
        String normalized = normalizeGuestId(guestId);
        if (normalized == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                    "Authentication or a guest session (X-Guest-Id) is required.");
        }
        return ChatCaller.guest(normalized);
    }

    private String normalizeGuestId(String guestId) {
        if (guestId == null) {
            return null;
        }
        String trimmed = guestId.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        if (trimmed.length() > MAX_GUEST_ID_LENGTH) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid guest session id.");
        }
        return trimmed;
    }
}
