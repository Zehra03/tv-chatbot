package com.paximum.paxassist.orchestrator.intent;

import java.util.ArrayList;
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
        return intent == IntentType.FILTER || intent == IntentType.CLEAR_FILTER;
    }

    @Override
    public OrchestrationResult handle(OrchestrationContext context) {
        List<Object> current = context.session().getLastApiResultCards();
        if (current == null || current.isEmpty()) {
            return OrchestrationResult.message(
                    "Önce bir otel veya uçuş araması yapmalıyız. Ne aramak istersiniz?");
        }

        if (context.intent() == IntentType.CLEAR_FILTER) {
            context.session().getAccumulatedCriteria().remove("limit");
            context.session().getAccumulatedCriteria().remove("stars");
            context.session().getAccumulatedCriteria().remove("maxStars");
            context.session().getAccumulatedCriteria().remove("boardType");
            context.session().getAccumulatedCriteria().remove("features");
            context.session().getAccumulatedCriteria().remove("sortBy");
            context.session().getAccumulatedCriteria().remove("hotelMaxPrice");
            context.session().getAccumulatedCriteria().remove("flightMaxPrice");
            context.session().getAccumulatedCriteria().remove("directFlight");
            context.session().getAccumulatedCriteria().remove("departTimeRange");

            context.session().setLastResultCards(new ArrayList<>(current));
            return OrchestrationResult.cards("Filtreler temizlendi. Tüm sonuçlar listeleniyor:", current);
        }

        if (context.criteria() == null) {
            return OrchestrationResult.message("Filtreleme kriteri anlaşılamadı.");
        }

        List<Object> filtered = new ArrayList<>(current);

        // Apply filters in sequence
        Integer hotelPrice = context.criteria().hotelMaxPrice();
        Integer flightPrice = context.criteria().flightMaxPrice();
        Integer maxPrice = hotelPrice != null ? hotelPrice : flightPrice;
        filtered = ResultFilters.applyMaxPrice(filtered, maxPrice);

        filtered = ResultFilters.applyBoardType(filtered, context.criteria().boardType());
        filtered = ResultFilters.applyFeatures(filtered, context.criteria().features());
        filtered = ResultFilters.applyStars(filtered, context.criteria().stars(), context.criteria().maxStars());
        filtered = ResultFilters.applyDirectFlight(filtered, context.criteria().directFlight());
        filtered = ResultFilters.applyDepartTimeRange(filtered, context.criteria().departTimeRange());

        if (filtered.isEmpty()) {
            return OrchestrationResult.message("Mevcut sonuçlar arasında bu kriterlere uygun sonuç bulamadım.");
        }

        String sortBy = context.criteria().sortBy();
        filtered = ResultFilters.applySort(filtered, sortBy);

        filtered = ResultFilters.applyLimit(filtered, context.criteria().limit());

        context.session().setLastResultCards(filtered);
        return OrchestrationResult.cards("İşte filtrelenmiş sonuçlar:", filtered);
    }
}
