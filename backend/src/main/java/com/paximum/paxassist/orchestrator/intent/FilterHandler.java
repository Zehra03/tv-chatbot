package com.paximum.paxassist.orchestrator.intent;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.springframework.stereotype.Component;

import com.paximum.paxassist.ai.IntentType;
import com.paximum.paxassist.orchestrator.OrchestrationContext;
import com.paximum.paxassist.orchestrator.OrchestrationResult;

/**
 * Handles FILTER intent: re-orders the cards already shown ({@code session.lastResultCards})
 * in memory — no new TourVisio call. Supports {@code price_asc}, {@code price_desc},
 * {@code stars_desc} (stars apply to hotels only). Operates on whatever the active result list
 * holds, so it works for both hotel and flight results.
 */
@Component
public class FilterHandler implements IntentHandler {

    @Override
    public boolean supports(IntentType intent) {
        return intent == IntentType.FILTER;
    }

    @Override
    public OrchestrationResult handle(OrchestrationContext context) {
        List<Object> current = context.session().getLastResultCards();
        if (current == null || current.isEmpty()) {
            return OrchestrationResult.message(
                    "Önce bir otel veya uçuş araması yapmalıyız. Ne aramak istersiniz?");
        }

        String sortBy = context.criteria() != null ? context.criteria().sortBy() : null;
        Comparator<Object> comparator = comparatorFor(sortBy);
        if (comparator == null) {
            return OrchestrationResult.message(
                    "Sonuçları nasıl sıralamamı istersiniz? Örneğin \"en ucuzdan\" veya \"en yüksek yıldızlı\".");
        }

        List<Object> sorted = new ArrayList<>(current);
        sorted.sort(comparator);
        context.session().setLastResultCards(sorted);
        return OrchestrationResult.cards("İşte güncellenmiş sıralama:", sorted);
    }

    private Comparator<Object> comparatorFor(String sortBy) {
        if (sortBy == null) {
            return null;
        }
        return switch (sortBy) {
            case "price_asc" ->
                    Comparator.comparing(ProductCards::priceOf, Comparator.nullsLast(Comparator.naturalOrder()));
            case "price_desc" ->
                    Comparator.comparing(ProductCards::priceOf, Comparator.nullsLast(Comparator.reverseOrder()));
            case "stars_desc" ->
                    Comparator.comparing(ProductCards::starsOf, Comparator.nullsLast(Comparator.reverseOrder()));
            default -> null;
        };
    }
}
