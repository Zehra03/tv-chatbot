package com.paximum.paxassist.orchestrator.intent;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.paximum.paxassist.ai.IntentType;
import com.paximum.paxassist.ai.SlotCriteria;
import com.paximum.paxassist.hotel.HotelSearchService;
import com.paximum.paxassist.hotel.dto.HotelSearchRequest;
import com.paximum.paxassist.orchestrator.OrchestrationContext;
import com.paximum.paxassist.orchestrator.OrchestrationResult;
import com.paximum.paxassist.orchestrator.mapper.HotelCriteriaMapper;
import com.paximum.paxassist.orchestrator.slot.SlotFillingService;

/**
 * Handles {@link IntentType#DATE_ALTERNATIVES}: after a hotel search returned nothing, the user
 * asks "başka hangi tarihte müsait?" without naming a new date. Instead of re-running the same
 * (empty) search, this probes TourVisio for nearby check-in dates that actually have availability
 * (via {@link HotelSearchService#suggestAvailableCheckInDates}) and offers the real ones back.
 *
 * <p>Only dates the provider confirms are surfaced — never fabricated. The user can then reply with
 * one of the offered dates, which flows back through {@link HotelSearchHandler} as a normal search.
 *
 * <p>Scoped to the hotel domain (the reported case); a bare date-alternatives ask with no
 * accumulated destination gets a gentle nudge rather than a wasted probe.
 */
@Component
public class DateSuggestionHandler implements IntentHandler {

    private final SlotFillingService slotFilling;
    private final HotelCriteriaMapper mapper;
    private final HotelSearchService hotelSearchService;
    private final int stepDays;
    private final int windowDays;
    private final int maxSuggestions;

    public DateSuggestionHandler(SlotFillingService slotFilling,
                                 HotelCriteriaMapper mapper,
                                 HotelSearchService hotelSearchService,
                                 @Value("${app.date-suggestion.step-days:2}") int stepDays,
                                 @Value("${app.date-suggestion.window-days:14}") int windowDays,
                                 @Value("${app.date-suggestion.max-suggestions:3}") int maxSuggestions) {
        this.slotFilling = slotFilling;
        this.mapper = mapper;
        this.hotelSearchService = hotelSearchService;
        this.stepDays = stepDays;
        this.windowDays = windowDays;
        this.maxSuggestions = maxSuggestions;
    }

    @Override
    public boolean supports(IntentType intent) {
        return intent == IntentType.DATE_ALTERNATIVES;
    }

    @Override
    public OrchestrationResult handle(OrchestrationContext context) {
        SlotCriteria merged = slotFilling.accumulate(context.session(), context.criteria());

        // No destination yet → nothing to probe. Nudge instead of guessing (keeps us honest).
        if (merged.location() == null || merged.location().isBlank()) {
            return OrchestrationResult.message(
                    "Hangi şehir için uygun tarih önereyim? Önce nereye gitmek istediğinizi söyleyin.");
        }

        HotelSearchRequest base = mapper.toRequest(merged);
        List<String> dates =
                hotelSearchService.suggestAvailableCheckInDates(base, stepDays, windowDays, maxSuggestions);

        context.session().setActiveDomain("HOTEL");

        if (dates.isEmpty()) {
            return OrchestrationResult.message(
                    merged.location() + " için yakın tarihlerde müsaitlik bulamadım. "
                            + "Farklı bir şehir ya da daha ileri bir tarih deneyebilir misiniz?");
        }

        return OrchestrationResult.message(
                "Şu giriş tarihlerinde müsaitlik var: " + String.join(", ", dates) + ". "
                        + "İstediğiniz tarihi yazın, o tarih için otelleri getireyim.");
    }
}
