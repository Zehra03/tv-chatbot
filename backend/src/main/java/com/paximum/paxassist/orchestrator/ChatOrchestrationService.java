package com.paximum.paxassist.orchestrator;

import java.util.List;

import org.springframework.stereotype.Service;

import com.paximum.paxassist.ai.ChatHistoryEntry;
import com.paximum.paxassist.ai.IntentExtractionResult;
import com.paximum.paxassist.ai.IntentExtractionService;
import com.paximum.paxassist.ai.IntentType;
import com.paximum.paxassist.chat.domain.ChatCaller;
import com.paximum.paxassist.chat.domain.ChatSession;
import com.paximum.paxassist.chat.service.ChatSessionStore;
import com.paximum.paxassist.chat.service.ReplyLocalizer;
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
    private final ReplyLocalizer replyLocalizer;

    public ChatOrchestrationService(GuardOrchestrator guard,
                                    GuardAuditLogger guardAuditLogger,
                                    IntentExtractionService intentExtraction,
                                    ChatSessionStore sessionStore,
                                    IntentRouter intentRouter,
                                    ReplyLocalizer replyLocalizer) {
        this.guard = guard;
        this.guardAuditLogger = guardAuditLogger;
        this.intentExtraction = intentExtraction;
        this.sessionStore = sessionStore;
        this.intentRouter = intentRouter;
        this.replyLocalizer = replyLocalizer;
    }

    public OrchestrationOutcome handle(String sessionId, String userMessage, ChatCaller caller) {
        ChatSession session = sessionStore.getOrCreate(sessionId, caller);

        // Stateful abuse tracking is keyed by user id. Guests (userId null) are covered by the
        // stateless content guard below + IP rate limiting, but not the consecutive-out-of-scope
        // block (the guard no-ops on a null key) — an accepted limitation for anonymous callers.
        Long userId = caller.userId();

        // 1. Guard — fail fast before any LLM call. A blocked request is audited and answered
        //    with the safe standard message; the detailed reason is never leaked to the client.
        //    assertNotBlocked short-circuits a user already under a temporary out-of-scope block,
        //    so a blocked user never triggers an LLM call.
        try {
            guard.assertNotBlocked(userId);
            guard.processInput(userMessage);
        } catch (GuardBlockedException e) {
            return blockedOutcome(session, userMessage, e);
        }

        // 2. Intent + slot extraction over the prior conversation history.
        List<ChatHistoryEntry> history = toHistory(session);
        IntentExtractionResult extraction = intentExtraction.extract(userMessage, history);

        // 2b. Track consecutive out-of-scope (OTHER) turns. Crossing the threshold blocks the user
        //     temporarily; this turn already returns the block message (later turns are stopped at
        //     step 1). A real search resets the streak.
        //     A greeting is neither: it must not extend the streak, but it must not reset it either,
        //     otherwise slipping a "merhaba" between out-of-scope messages would defeat the block.
        if (extraction.intent() != IntentType.GREETING) {
            try {
                guard.registerOutOfScope(userId, extraction.intent() == IntentType.OTHER);
            } catch (GuardBlockedException e) {
                return blockedOutcome(session, userMessage, e);
            }
        }

        // 3. Route to the intent-specific handler (Strategy).
        OrchestrationContext context =
                new OrchestrationContext(session, userMessage, extraction.intent(), extraction.criteria());
        OrchestrationResult result = intentRouter.route(extraction.intent()).handle(context);

        // 3b. Localize the reply into the language of THIS message. The localizer is the single
        //     source of the output language for EVERY routed reply — the deterministic search flows
        //     AND the free-form OTHER reply (the assistant's own prompt is Turkish-heavy and cannot
        //     be trusted to answer in-language on its own). A null/Turkish language (bare greeting,
        //     parse failure, Turkish user) is a no-op, so only a non-Turkish user pays for a
        //     translation. The localized text is what we persist, keeping the transcript in the
        //     user's language for later context.
        if (replyLocalizer.shouldLocalize(extraction.detectedLanguage())) {
            result = localizeReply(result, extraction.detectedLanguage());
        }

        // 4. Persist the turn (append user + assistant messages) and return.
        session.addMessage("user", userMessage);
        session.addMessage("assistant", result.reply());
        sessionStore.save(session);
        return new OrchestrationOutcome(result.withSessionId(session.getId()), session);
    }

    /**
     * Audits a guard block and returns the safe rejection outcome without persisting the turn — the
     * shared exit for both the pre-LLM content/abuse block and the post-extraction threshold block.
     */
    private OrchestrationOutcome blockedOutcome(ChatSession session, String userMessage,
                                                GuardBlockedException e) {
        guardAuditLogger.logBlockedRequestAsync(userMessage, e.getDetailedReason());
        OrchestrationResult blocked = OrchestrationResult.message(e.getMessage())
                .withSessionId(session.getId());
        return new OrchestrationOutcome(blocked, session);
    }

    /**
     * Translates only the natural-language parts of a result — reply, the mirrored pending question,
     * and any disambiguation option label/value — leaving the structured cards and flags untouched.
     * The pending question mirrors the reply on a clarify result, so it reuses the same translation
     * instead of paying for a second call.
     */
    private OrchestrationResult localizeReply(OrchestrationResult result, String language) {
        String localizedReply = replyLocalizer.localize(result.reply(), language);
        String localizedPending = result.pendingQuestion() == null ? null
                : result.pendingQuestion().equals(result.reply())
                        ? localizedReply
                        : replyLocalizer.localize(result.pendingQuestion(), language);
        List<ChoiceOption> localizedOptions = result.options().isEmpty()
                ? result.options()
                : result.options().stream()
                        .map(option -> new ChoiceOption(
                                replyLocalizer.localize(option.label(), language),
                                replyLocalizer.localize(option.value(), language)))
                        .toList();
        return result.withLocalizedText(localizedReply, localizedPending, localizedOptions);
    }

    private List<ChatHistoryEntry> toHistory(ChatSession session) {
        return session.getMessages().stream()
                .map(message -> new ChatHistoryEntry(message.role(), message.content()))
                .toList();
    }
}
