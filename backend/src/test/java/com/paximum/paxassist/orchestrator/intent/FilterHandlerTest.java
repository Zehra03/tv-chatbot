package com.paximum.paxassist.orchestrator.intent;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

import com.paximum.paxassist.ai.IntentType;
import com.paximum.paxassist.ai.SlotCriteria;
import com.paximum.paxassist.chat.domain.ChatSession;
import com.paximum.paxassist.hotel.HotelProduct;
import com.paximum.paxassist.orchestrator.OrchestrationContext;
import com.paximum.paxassist.orchestrator.OrchestrationResult;

import static org.assertj.core.api.Assertions.assertThat;

class FilterHandlerTest {

    private final FilterHandler handler = new FilterHandler();
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final HotelProduct CHEAP_3STAR = new HotelProduct("H3", "Budget", "Izmir", 3, new BigDecimal("350"), "EUR", "BB", true);
    private static final HotelProduct MID_5STAR = new HotelProduct("H1", "Rixos", "Antalya", 5, new BigDecimal("1500"), "EUR", "AI", true);
    private static final HotelProduct PRICEY_4STAR = new HotelProduct("H2", "Titanic", "Antalya", 4, new BigDecimal("2000"), "EUR", "AI", true);

    private OrchestrationResult filter(List<Object> cards, String sortBy) {
        ChatSession session = new ChatSession("s1");
        session.setLastApiResultCards(cards);
        session.setLastResultCards(cards);
        SlotCriteria criteria = sortBy == null
                ? null
                : objectMapper.convertValue(Map.of("sortBy", sortBy), SlotCriteria.class);
        return handler.handle(new OrchestrationContext(session, "msg", IntentType.FILTER, criteria));
    }

    @Test
    void sortsByPriceAscending() {
        OrchestrationResult result = filter(List.of(MID_5STAR, PRICEY_4STAR, CHEAP_3STAR), "price_asc");
        assertThat(result.cards()).containsExactly(CHEAP_3STAR, MID_5STAR, PRICEY_4STAR);
    }

    @Test
    void sortsByPriceDescending() {
        OrchestrationResult result = filter(List.of(MID_5STAR, PRICEY_4STAR, CHEAP_3STAR), "price_desc");
        assertThat(result.cards()).containsExactly(PRICEY_4STAR, MID_5STAR, CHEAP_3STAR);
    }

    @Test
    void sortsByStarsDescending() {
        OrchestrationResult result = filter(List.of(CHEAP_3STAR, MID_5STAR, PRICEY_4STAR), "stars_desc");
        assertThat(result.cards()).containsExactly(MID_5STAR, PRICEY_4STAR, CHEAP_3STAR);
    }

    @Test
    void repliesWhenNoResultsToFilter() {
        OrchestrationResult result = filter(List.of(), "price_asc");
        assertThat(result.cards()).isEmpty();
        assertThat(result.reply()).contains("Önce");
    }

    @Test
    void asksForCriteriaWhenNoCriteria() {
        OrchestrationResult result = filter(List.of(MID_5STAR), null);
        assertThat(result.cards()).isEmpty();
        assertThat(result.reply()).contains("kriteri anlaşılamadı");
    }

    @Test
    void filtersByLimit() {
        ChatSession session = new ChatSession("s1");
        session.setLastApiResultCards(List.of(MID_5STAR, PRICEY_4STAR, CHEAP_3STAR));
        session.setLastResultCards(List.of(MID_5STAR, PRICEY_4STAR, CHEAP_3STAR));
        SlotCriteria criteria = objectMapper.convertValue(Map.of("limit", 2), SlotCriteria.class);
        OrchestrationResult result = handler.handle(new OrchestrationContext(session, "msg", IntentType.FILTER, criteria));
        
        assertThat(result.cards()).containsExactly(MID_5STAR, PRICEY_4STAR);
    }

    @Test
    void filtersByStarsAndSorts() {
        ChatSession session = new ChatSession("s1");
        session.setLastApiResultCards(List.of(CHEAP_3STAR, MID_5STAR, PRICEY_4STAR));
        session.setLastResultCards(List.of(CHEAP_3STAR, MID_5STAR, PRICEY_4STAR));
        SlotCriteria criteria = objectMapper.convertValue(Map.of("stars", 4, "sortBy", "price_asc"), SlotCriteria.class);
        OrchestrationResult result = handler.handle(new OrchestrationContext(session, "msg", IntentType.FILTER, criteria));
        
        assertThat(result.cards()).containsExactly(MID_5STAR, PRICEY_4STAR); // Wait, MID_5STAR is 1500, PRICEY_4STAR is 2000. So MID then PRICEY.
    }
}
