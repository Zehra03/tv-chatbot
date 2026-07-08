package com.paximum.paxassist.orchestrator.intent;

import java.util.List;

import org.springframework.stereotype.Component;

import com.paximum.paxassist.ai.IntentType;
import com.paximum.paxassist.chat.service.ChatService;
import com.paximum.paxassist.orchestrator.OrchestrationContext;
import com.paximum.paxassist.orchestrator.OrchestrationResult;
import com.paximum.paxassist.orchestrator.refine.Evaluator;
import com.paximum.paxassist.orchestrator.refine.EvaluatorOptimizer;
import com.paximum.paxassist.orchestrator.refine.Generator;

/**
 * Handles OTHER intent (greeting, out-of-scope, ambiguous) via the conversational {@link ChatService},
 * wrapped in the evaluator-optimizer loop so the reply is checked against the guardrails (no fabricated
 * prices, no prompt leak, no booking promise, on-topic Turkish) before it reaches the user.
 *
 * <p>Also the {@link IntentRouter}'s default when no handler matches.
 */
@Component
public class FallbackHandler implements IntentHandler {

    private static final String SAFE_FALLBACK =
            "Bu konuda yardımcı olamıyorum. Otel veya uçuş araması için buradayım.";

    private final ChatService chatService;
    private final EvaluatorOptimizer evaluatorOptimizer;
    private final Evaluator evaluator;

    public FallbackHandler(ChatService chatService,
                           EvaluatorOptimizer evaluatorOptimizer,
                           Evaluator evaluator) {
        this.chatService = chatService;
        this.evaluatorOptimizer = evaluatorOptimizer;
        this.evaluator = evaluator;
    }

    @Override
    public boolean supports(IntentType intent) {
        return intent == IntentType.OTHER;
    }

    @Override
    public OrchestrationResult handle(OrchestrationContext context) {
        String userMessage = context.userMessage();

        // Generator: on the first round send the raw message; on a retry, append the critic's
        // feedback so the model corrects its previous reply.
        Generator generator = feedback -> {
            String prompt = feedback.isEmpty()
                    ? userMessage
                    : userMessage + "\n\n[Sistem notu: Önceki yanıtın şu nedenle uygun değildi, lütfen düzelt: "
                            + String.join(" | ", feedback) + "]";
            return chatService.chat(prompt).reply();
        };

        // No source facts for a general conversational reply → empty context (pure guardrail check).
        String reply = evaluatorOptimizer.refine(generator, evaluator, "", SAFE_FALLBACK);
        return OrchestrationResult.message(reply);
    }
}
