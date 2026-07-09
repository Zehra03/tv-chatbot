package com.paximum.paxassist.orchestrator.intent;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import com.paximum.paxassist.ai.IntentType;
import com.paximum.paxassist.orchestrator.OrchestrationContext;
import com.paximum.paxassist.orchestrator.OrchestrationResult;

/**
 * Handles SELECT intent: resolves the user's free-text selection reference ("1", "ilk",
 * "en ucuz olan") against the last result cards and routes the chosen product to the
 * reservation form. It NEVER books — it only sets {@code redirectToReservation=true} and hands
 * the product over, honouring the invariant "the chatbot never books" (the 0-token safe zone).
 */
@Component
public class SelectHandler implements IntentHandler {

    private static final Locale TR = Locale.of("tr", "TR");
    private static final Pattern FIRST_NUMBER = Pattern.compile("\\d+");
    private static final Map<String, Integer> ORDINAL_WORDS = Map.of(
            "birinci", 1, "ikinci", 2, "üçüncü", 3, "dördüncü", 4, "beşinci", 5);

    @Override
    public boolean supports(IntentType intent) {
        return intent == IntentType.SELECT;
    }

    @Override
    public OrchestrationResult handle(OrchestrationContext context) {
        List<Object> cards = context.session().getLastResultCards();
        if (cards == null || cards.isEmpty()) {
            return OrchestrationResult.message(
                    "Seçim yapabilmeniz için önce bir arama yapmalıyız. Ne aramak istersiniz?");
        }

        String reference = context.criteria() != null ? context.criteria().selectionReference() : null;
        Object selected = resolve(cards, reference);
        if (selected == null) {
            return OrchestrationResult.message(
                    "Hangi sonucu seçtiğinizi anlayamadım. Örneğin \"1\", \"ilk\" ya da \"en ucuz olan\" diyebilirsiniz.");
        }
        return OrchestrationResult.redirect("Seçiminizi rezervasyon adımına aktarıyorum.", selected);
    }

    private Object resolve(List<Object> cards, String reference) {
        if (reference == null || reference.isBlank()) {
            return null;
        }
        String ref = reference.trim().toLowerCase(TR);

        if (ref.contains("en ucuz")) {
            return byPrice(cards, true);
        }
        if (ref.contains("en pahalı") || ref.contains("en pahali")) {
            return byPrice(cards, false);
        }
        if (ref.startsWith("ilk")) {
            return cards.get(0);
        }
        if (ref.startsWith("son")) {
            return cards.get(cards.size() - 1);
        }
        for (Map.Entry<String, Integer> entry : ORDINAL_WORDS.entrySet()) {
            if (ref.startsWith(entry.getKey())) {
                return atIndex(cards, entry.getValue() - 1);
            }
        }
        Matcher matcher = FIRST_NUMBER.matcher(ref);
        if (matcher.find()) {
            return atIndex(cards, Integer.parseInt(matcher.group()) - 1);
        }
        return null;
    }

    private Object atIndex(List<Object> cards, int index) {
        return (index >= 0 && index < cards.size()) ? cards.get(index) : null;
    }

    private Object byPrice(List<Object> cards, boolean cheapest) {
        Object best = null;
        BigDecimal bestPrice = null;
        for (Object card : cards) {
            BigDecimal price = ProductCards.priceOf(card);
            if (price == null) {
                continue;
            }
            if (bestPrice == null
                    || (cheapest ? price.compareTo(bestPrice) < 0 : price.compareTo(bestPrice) > 0)) {
                bestPrice = price;
                best = card;
            }
        }
        return best;
    }
}
