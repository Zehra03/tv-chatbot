package com.paximum.paxassist.orchestrator;

import java.util.List;

/**
 * The user-facing outcome an {@link com.paximum.paxassist.orchestrator.intent.IntentHandler}
 * produces for one turn. The chat layer maps this onto the frontend contract
 * ({@code chat.dto.SendMessageResponseDto}) via {@code chat.service.ChatResponseAssembler}.
 *
 * <p>Handlers build results WITHOUT a session id (they don't own session identity);
 * the coordinator stamps it via {@link #withSessionId(String)} before returning.
 * Four shapes cover every branch:
 * <ul>
 *   <li>{@link #message(String)}  — a plain reply (greeting, rejection, select error)</li>
 *   <li>{@link #clarify(String, String)} — a slot-filling clarifying question for a domain</li>
 *   <li>{@link #cards(String, List)} — a reply plus a list of result cards</li>
 *   <li>{@link #redirect(String, Object)} — route the chosen product to the reservation form</li>
 * </ul>
 *
 * @param reply                 assistant text to show
 * @param cards                 result cards (hotel/flight products); empty when not a search result
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
        boolean redirectToReservation,
        Object selectedProduct,
        String sessionId,
        String pendingQuestion,
        String domain
) {

    public static OrchestrationResult message(String reply) {
        return new OrchestrationResult(reply, List.of(), false, null, null, null, null);
    }

    /**
     * A clarifying question emitted while slot-filling is still incomplete. Both {@code reply} and
     * {@code pendingQuestion} carry the question text; {@code domain} tells the assembler which
     * criteria set is being collected.
     */
    public static OrchestrationResult clarify(String question, String domain) {
        return new OrchestrationResult(question, List.of(), false, null, null, question, domain);
    }

    public static OrchestrationResult cards(String reply, List<Object> cards) {
        return new OrchestrationResult(reply, cards, false, null, null, null, null);
    }

    public static OrchestrationResult redirect(String reply, Object selectedProduct) {
        return new OrchestrationResult(reply, List.of(), true, selectedProduct, null, null, null);
    }

    /** Coordinator stamps the session id onto a handler's result before returning it. */
    public OrchestrationResult withSessionId(String id) {
        return new OrchestrationResult(reply, cards, redirectToReservation, selectedProduct, id,
                pendingQuestion, domain);
    }
}
