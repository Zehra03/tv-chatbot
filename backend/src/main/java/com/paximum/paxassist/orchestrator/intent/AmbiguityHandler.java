package com.paximum.paxassist.orchestrator.intent;

import java.util.List;

import org.springframework.stereotype.Component;

import com.paximum.paxassist.ai.IntentType;
import com.paximum.paxassist.orchestrator.ChoiceOption;
import com.paximum.paxassist.orchestrator.OrchestrationContext;
import com.paximum.paxassist.orchestrator.OrchestrationResult;

/**
 * Handles {@link IntentType#AMBIGUOUS} — the intent extractor couldn't decide whether the user
 * wants a hotel or a flight search (e.g. a bare place name like "Antalya"). Instead of guessing or
 * asking an open question, it returns a disambiguation card with two selectable options.
 *
 * <p>Each option's value re-states the user's original message with the chosen domain
 * ("{message} için otel arıyorum"), so picking one carries the original context (e.g. the city)
 * into the next turn, where the normal HOTEL/FLIGHT pipeline runs. This is the first (intent-level)
 * disambiguation class from {@code docs/chatbot-test-senaryolari.md} §6; other classes ("2 2",
 * "çocuksuz otel"...) can reuse the same {@code choices} shape later.
 */
@Component
public class AmbiguityHandler implements IntentHandler {

    static final String INTENT_QUESTION =
            "Otel araması mı yoksa uçuş araması mı yapmak istersin?";

    @Override
    public boolean supports(IntentType intent) {
        return intent == IntentType.AMBIGUOUS;
    }

    @Override
    public OrchestrationResult handle(OrchestrationContext context) {
        String message = context.userMessage().trim();
        List<ChoiceOption> options = List.of(
                new ChoiceOption("Otel ara", message + " için otel arıyorum"),
                new ChoiceOption("Uçuş ara", message + " için uçuş arıyorum"));
        return OrchestrationResult.choices(INTENT_QUESTION, options);
    }
}
