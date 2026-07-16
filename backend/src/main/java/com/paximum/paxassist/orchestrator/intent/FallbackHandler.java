package com.paximum.paxassist.orchestrator.intent;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.paximum.paxassist.ai.IntentType;
import com.paximum.paxassist.chat.service.ChatService;
import com.paximum.paxassist.orchestrator.OrchestrationContext;
import com.paximum.paxassist.orchestrator.OrchestrationResult;
import com.paximum.paxassist.validator.ValidationOrchestrator;
import com.paximum.paxassist.validator.ValidationOutcome;
import com.paximum.paxassist.validator.ValidationResult;

/**
 * Handles OTHER intent (greeting, out-of-scope, ambiguous) via the conversational {@link ChatService},
 * with the reply checked by the second-layer {@link ValidationOrchestrator} (no fabricated prices, no
 * prompt leak, no booking promise, on-topic Turkish) before it reaches the user.
 *
 * <p>Replaces the earlier evaluator-optimizer loop: one generate + one validator pass, with at most a
 * single feedback-driven correction (the validator's own {@code retryAllowed} ceiling governs when to
 * stop), then a safe fallback. This trades the old loop's up-to-{@code maxIterations}×2 main-model
 * round-trips for far fewer calls, and runs the critic on the validator's dedicated model instead of
 * the main chat model.
 *
 * <p>Also the {@link IntentRouter}'s default when no handler matches.
 */
@Component
public class FallbackHandler implements IntentHandler {

    private static final Logger log = LoggerFactory.getLogger(FallbackHandler.class);

    private static final String SAFE_FALLBACK =
            "Bu konuda yardımcı olamıyorum. Otel veya uçuş araması için buradayım.";

    // A general conversational reply has no source facts to ground against, so the validator gets an
    // empty context and only enforces the format/policy rules (dates, scope, injection), not
    // fabrication-vs-context. Kept as a named constant to make that intent explicit.
    private static final String NO_GROUNDING_CONTEXT = "";

    private final ChatService chatService;
    private final ValidationOrchestrator validationOrchestrator;

    public FallbackHandler(ChatService chatService, ValidationOrchestrator validationOrchestrator) {
        this.chatService = chatService;
        this.validationOrchestrator = validationOrchestrator;
    }

    @Override
    public boolean supports(IntentType intent) {
        return intent == IntentType.OTHER;
    }

    @Override
    public OrchestrationResult handle(OrchestrationContext context) {
        String userMessage = context.userMessage();
        List<String> feedback = new ArrayList<>();

        // Terminates via the validator's retryAllowed ceiling: once it stops offering a retry
        // (REJECTED and attempt >= max-retries) we return the safe fallback; APPROVED returns earlier.
        for (int attempt = 1; ; attempt++) {
            String candidate = generate(userMessage, feedback);

            ValidationOutcome outcome;
            try {
                outcome = validationOrchestrator.validate(context.session().getId(), userMessage, candidate,
                        NO_GROUNDING_CONTEXT, attempt);
            } catch (RuntimeException e) {
                // Fail-open: the validator is a second safety layer. If it is unavailable the reply was
                // already produced under the assistant's own guardrails, so show it rather than failing
                // the whole turn (mirrors the old evaluator-optimizer's fail-open-on-critic-outage).
                log.warn("Validator unavailable on attempt {}, accepting candidate as-is: {}",
                        attempt, e.getMessage());
                return OrchestrationResult.message(candidate);
            }

            if (outcome.result().verdict() == ValidationResult.Verdict.APPROVED) {
                return OrchestrationResult.message(candidate);
            }

            // REJECTED — a rejected candidate is never shown. Retry with the critic's feedback while
            // the validator still allows it; otherwise fall back to the safe canned message.
            if (!outcome.retryAllowed()) {
                log.info("Validator rejected the reply and no retry remains; returning safe fallback.");
                return OrchestrationResult.message(SAFE_FALLBACK);
            }
            String critic = outcome.result().feedback();
            if (critic != null && !critic.isBlank()) {
                feedback.add(critic);
            }
        }
    }

    /**
     * First round sends the raw message; on a retry the validator's feedback is appended so the model
     * corrects its previous reply.
     */
    private String generate(String userMessage, List<String> feedback) {
        String prompt = feedback.isEmpty()
                ? userMessage
                : userMessage + "\n\n[Sistem notu: Önceki yanıtın şu nedenle uygun değildi, lütfen düzelt: "
                        + String.join(" | ", feedback) + "]";
        return chatService.chat(prompt).reply();
    }
}
