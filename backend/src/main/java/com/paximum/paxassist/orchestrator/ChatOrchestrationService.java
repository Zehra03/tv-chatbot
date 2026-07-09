package com.paximum.paxassist.orchestrator;

import java.util.List;

import org.springframework.stereotype.Service;

import com.paximum.paxassist.ai.ChatHistoryEntry;
import com.paximum.paxassist.ai.IntentExtractionResult;
import com.paximum.paxassist.ai.IntentExtractionService;
import com.paximum.paxassist.chat.domain.ChatSession;
import com.paximum.paxassist.chat.service.ChatSessionStore;
import com.paximum.paxassist.guard.GuardBlockedException;
import com.paximum.paxassist.guard.GuardAuditLogger;
import com.paximum.paxassist.guard.GuardOrchestrator;
import com.paximum.paxassist.orchestrator.intent.IntentRouter;

/**
 * Thin coordinator that owns ONLY the fixed pipeline skeleton: guard → extract → route → persist.
 * It contains no intent {@code switch}, no request mapping, and no criteria manipulation — those
 * live in the handlers, mappers and slot layer respectively. This deliberate leanness (exactly
 * five collaborators) is the primary defence against the orchestrator becoming a God object.
 *
 * @see com.paximum.paxassist.orchestrator.intent.IntentRouter
 */
@Service
public class ChatOrchestrationService {

    private final GuardOrchestrator guard;
    private final GuardAuditLogger guardAuditLogger;
    private final IntentExtractionService intentExtraction;
    private final ChatSessionStore sessionStore;
    private final IntentRouter intentRouter;

    public ChatOrchestrationService(GuardOrchestrator guard,
                                    GuardAuditLogger guardAuditLogger,
                                    IntentExtractionService intentExtraction,
                                    ChatSessionStore sessionStore,
                                    IntentRouter intentRouter) {
        this.guard = guard;
        this.guardAuditLogger = guardAuditLogger;
        this.intentExtraction = intentExtraction;
        this.sessionStore = sessionStore;
        this.intentRouter = intentRouter;
    }

    public OrchestrationOutcome handle(String sessionId, String userMessage, Long userId) {
        ChatSession session = sessionStore.getOrCreate(sessionId, userId);

        // 1. Guard — fail fast before any LLM call. A blocked request is audited and answered
        //    with the safe standard message; the detailed reason is never leaked to the client.
        try {
            guard.processInput(userMessage);
        } catch (GuardBlockedException e) {
            guardAuditLogger.logBlockedRequestAsync(userMessage, e.getDetailedReason());
            OrchestrationResult blocked = OrchestrationResult.message(e.getMessage())
                    .withSessionId(session.getId());
            return new OrchestrationOutcome(blocked, session);
        }

        // 2. Intent + slot extraction over the prior conversation history.
        List<ChatHistoryEntry> history = toHistory(session);
        IntentExtractionResult extraction = intentExtraction.extract(userMessage, history);

        // 3. Route to the intent-specific handler (Strategy).
        OrchestrationContext context =
                new OrchestrationContext(session, userMessage, extraction.intent(), extraction.criteria());
        OrchestrationResult result = intentRouter.route(extraction.intent()).handle(context);

        // 4. Persist the turn (append user + assistant messages) and return.
        session.addMessage("user", userMessage);
        session.addMessage("assistant", result.reply());
        sessionStore.save(session);
        return new OrchestrationOutcome(result.withSessionId(session.getId()), session);
    }

    private List<ChatHistoryEntry> toHistory(ChatSession session) {
        return session.getMessages().stream()
                .map(message -> new ChatHistoryEntry(message.role(), message.content()))
                .toList();
    }
}
