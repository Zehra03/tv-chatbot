package com.paximum.paxassist.orchestrator;

import java.util.List;

/**
 * The user-facing outcome an {@link com.paximum.paxassist.orchestrator.intent.IntentHandler}
 * produces for one turn. The chat layer maps this onto the frontend contract
 * ({@code chat.dto.SendMessageResponseDto}) via {@code chat.service.ChatResponseAssembler}.
 *
 * <p>Handlers build results WITHOUT a session id (they don't own session identity);
 * the coordinator stamps it via {@link #withSessionId(String)} before returning.
 * Five shapes cover every branch:
 * <ul>
 *   <li>{@link #message(String)}  — a plain reply (greeting, rejection, select error)</li>
 *   <li>{@link #clarify(String, String)} — a slot-filling clarifying question for a domain</li>
 *   <li>{@link #cards(String, List)} — a reply plus a list of result cards</li>
 *   <li>{@link #choices(String, List)} — a disambiguation question plus selectable options</li>
 *   <li>{@link #redirect(String, Object)} — route the chosen product to the reservation form</li>
 * </ul>
 *
 * @param reply                 assistant text to show
 * @param cards                 result cards (hotel/flight products); empty when not a search result
 * @param options               disambiguation options ("hangisini demek istediniz?"); empty unless
 *                              this is a {@link #choices(String, List)} result
 * @param redirectToReservation true only on SELECT — the frontend then leaves the AI zone
 * @param selectedProduct       the chosen product when redirecting, else null
 * @param sessionId             filled in by the coordinator, null while inside a handler
 * @param pendingQuestion       non-null only when {@code reply} is a slot-filling clarifying
 *                              question the assistant is waiting an answer to; drives the
 *                              frontend's {@code pendingQuestion} field
 * @param domain                "hotel" | "flight" | null — the search domain this turn relates to,
 *                              so the assembler can label the accumulated criteria even mid
 *                              slot-filling (before {@code session.activeDomain} is set)
 */
public record OrchestrationResult(
        String reply,
        List<Object> cards,
        List<ChoiceOption> options,
        boolean redirectToReservation,
        Object selectedProduct,
        String sessionId,
        String pendingQuestion,
        String domain
) {

    public static OrchestrationResult message(String reply) {
        return new OrchestrationResult(reply, List.of(), List.of(), false, null, null, null, null);
    }

    /**
     * A clarifying question emitted while slot-filling is still incomplete. Both {@code reply} and
     * {@code pendingQuestion} carry the question text; {@code domain} tells the assembler which
     * criteria set is being collected.
     */
    public static OrchestrationResult clarify(String question, String domain) {
        return new OrchestrationResult(question, List.of(), List.of(), false, null, null, question, domain);
    }

    public static OrchestrationResult cards(String reply, List<Object> cards) {
        return new OrchestrationResult(reply, cards, List.of(), false, null, null, null, null);
    }

    /**
     * A disambiguation card: {@code question} plus the competing interpretations as selectable
     * {@code options}. Unlike {@link #clarify(String, String)} (one missing field, open answer),
     * this is used when the input has two or more enumerable interpretations. No {@code domain} or
     * {@code pendingQuestion} is set — the user resolves it by picking an option, which is sent as
     * the next chat turn.
     */
    public static OrchestrationResult choices(String question, List<ChoiceOption> options) {
        return new OrchestrationResult(question, List.of(), List.copyOf(options), false, null, null, null, null);
    }

    public static OrchestrationResult redirect(String reply, Object selectedProduct) {
        return new OrchestrationResult(reply, List.of(), List.of(), true, selectedProduct, null, null, null);
    }

    /** Coordinator stamps the session id onto a handler's result before returning it. */
    public OrchestrationResult withSessionId(String id) {
        return new OrchestrationResult(reply, cards, options, redirectToReservation, selectedProduct, id,
                pendingQuestion, domain);
    }

    /**
     * Returns a copy with the natural-language text swapped for its localized form: the reply, the
     * mirrored {@code pendingQuestion}, and the disambiguation {@code options}. Everything structured
     * — {@code cards}, the redirect flag, the selected product, {@code domain} — is carried through
     * untouched, so product data is never translated (only the wording the user reads is).
     */
    public OrchestrationResult withLocalizedText(String localizedReply, String localizedPendingQuestion,
                                                 List<ChoiceOption> localizedOptions) {
        return new OrchestrationResult(localizedReply, cards, localizedOptions, redirectToReservation,
                selectedProduct, sessionId, localizedPendingQuestion, domain);
    }
}
