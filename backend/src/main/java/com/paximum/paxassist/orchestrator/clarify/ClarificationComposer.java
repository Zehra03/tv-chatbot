package com.paximum.paxassist.orchestrator.clarify;

import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.paximum.paxassist.chat.domain.ChatMessage;
import com.paximum.paxassist.chat.domain.ChatSession;

/**
 * Decides HOW to ask for a missing slot, on top of {@link ClarificationCatalog}'s decision of WHAT
 * to ask. Two conversational faults are fixed here, both of which made the bot ask the identical
 * question twice in a row:
 *
 * <ol>
 *   <li><b>"fark etmez"</b> fills no slot, so the same search stayed incomplete and the same
 *       question came back. When the user has no preference, offer concrete options for the field
 *       instead — the user picks one and the search moves on.</li>
 *   <li><b>Any other answer the extractor could not use</b> (a typo, an unresolvable city) left the
 *       question repeating verbatim, which reads as if the bot ignored the user. A repeated ask is
 *       rephrased to say why the field is needed.</li>
 * </ol>
 *
 * <p>The offered options are examples the user chooses from, never a value the bot fills in on its
 * own: a search from an origin the user never picked would price a trip they did not ask for. Their
 * only claim is "this is a place you could depart from" — no availability or price is implied, so
 * nothing here fabricates provider data.
 */
@Component
public class ClarificationComposer {

    private final ClarificationCatalog catalog;
    private final NoPreferenceDetector noPreferenceDetector;

    public ClarificationComposer(ClarificationCatalog catalog, NoPreferenceDetector noPreferenceDetector) {
        this.catalog = catalog;
        this.noPreferenceDetector = noPreferenceDetector;
    }

    // Fields where "fark etmez" has an honest answer: a short list of places to pick from. Dates and
    // guest counts are absent on purpose — offering a date implies availability we have not checked,
    // and a passenger count is a fact about the user's trip that we must not invent.
    private static final Map<String, String> FLIGHT_OPTIONS = Map.of(
            "origin", "Kalkış noktası olmadan uçuş arayamıyorum. Örneğin İstanbul, Ankara veya "
                    + "İzmir'den bakabilirim — hangisi olsun?",
            "destination", "Varış noktası olmadan uçuş arayamıyorum. Örneğin Antalya, İzmir veya "
                    + "Londra'ya bakabilirim — hangisi olsun?"
    );

    private static final Map<String, String> HOTEL_OPTIONS = Map.of(
            "destination", "Şehri bilmeden otel arayamıyorum. Örneğin Antalya, İstanbul veya "
                    + "Bodrum'a bakabilirim — hangisi olsun?"
    );

    public String forFlight(ChatSession session, String userMessage, List<String> missingFields) {
        return compose(session, userMessage, missingFields, FLIGHT_OPTIONS,
                catalog.questionForFlight(missingFields));
    }

    public String forHotel(ChatSession session, String userMessage, List<String> missingFields) {
        return compose(session, userMessage, missingFields, HOTEL_OPTIONS,
                catalog.questionForHotel(missingFields));
    }

    private String compose(ChatSession session, String userMessage, List<String> missingFields,
                           Map<String, String> options, String question) {
        String field = firstField(missingFields);

        if (noPreferenceDetector.isNoPreference(userMessage)) {
            String offer = options.get(field);
            if (offer != null) {
                return offer;
            }
            // No honest option list for this field (a date, a guest count) — at least acknowledge the
            // answer, so "fark etmez" is not met with the same words as if nothing had been said.
            return "Bu bilgi olmadan aramayı yapamıyorum, bu yüzden senin belirtmen gerekiyor. " + question;
        }

        if (wasJustAsked(session, question)) {
            // The user answered but the field is still empty — most often the answer did not land as
            // a value we could use. Saying why beats asking the same words again.
            return "Bunu tam olarak alamadım, kusura bakma. Aramayı başlatmak için hâlâ bu bilgi "
                    + "gerekiyor: " + question;
        }

        return question;
    }

    private String firstField(List<String> missingFields) {
        return (missingFields == null || missingFields.isEmpty()) ? null : missingFields.get(0);
    }

    /**
     * True when the previous assistant turn already asked this exact question. The current turn is
     * not persisted yet, so the last assistant message is the previous reply. {@code startsWith}
     * rather than equality: a reply may carry a suffix (e.g. the traveller carry-over note).
     */
    private boolean wasJustAsked(ChatSession session, String question) {
        if (session == null) {
            return false;
        }
        List<ChatMessage> messages = session.getMessages();
        for (int i = messages.size() - 1; i >= 0; i--) {
            ChatMessage message = messages.get(i);
            if ("assistant".equals(message.role())) {
                return message.content() != null && message.content().startsWith(question);
            }
        }
        return false;
    }
}
