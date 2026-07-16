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

class SelectHandlerTest {

    private final SelectHandler handler = new SelectHandler();
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final HotelProduct H1 = new HotelProduct("H1", "Rixos", "Antalya", 5, new BigDecimal("1500"), "EUR", "AI", true);
    private static final HotelProduct H2 = new HotelProduct("H2", "Titanic", "Antalya", 5, new BigDecimal("2000"), "EUR", "AI", true);
    private static final HotelProduct H3 = new HotelProduct("H3", "Hilton", "Istanbul", 5, new BigDecimal("350"), "EUR", "BB", true);

    private OrchestrationResult select(List<Object> cards, String reference) {
        ChatSession session = new ChatSession("s1");
        session.setLastResultCards(cards);
        SlotCriteria criteria = reference == null
                ? null
                : objectMapper.convertValue(Map.of("selectionReference", reference), SlotCriteria.class);
        return handler.handle(new OrchestrationContext(session, "msg", IntentType.SELECT, criteria));
    }

    @Test
    void selectsByOneBasedNumber() {
        OrchestrationResult result = select(List.of(H1, H2, H3), "2");
        assertThat(result.redirectToReservation()).isTrue();
        assertThat(result.selectedProduct()).isEqualTo(H2);
    }

    @Test
    void selectsFirstByWord() {
        assertThat(select(List.of(H1, H2, H3), "ilk").selectedProduct()).isEqualTo(H1);
    }

    @Test
    void selectsLastByWord() {
        assertThat(select(List.of(H1, H2, H3), "sonuncu").selectedProduct()).isEqualTo(H3);
    }

    @Test
    void selectsCheapest() {
        assertThat(select(List.of(H1, H2, H3), "en ucuz olan").selectedProduct()).isEqualTo(H3);
    }

    @Test
    void selectsByOrdinalWord() {
        assertThat(select(List.of(H1, H2, H3), "birinci").selectedProduct()).isEqualTo(H1);
    }

    @Test
    void repliesWhenNoCardsYet() {
        OrchestrationResult result = select(List.of(), "1");
        assertThat(result.redirectToReservation()).isFalse();
        assertThat(result.selectedProduct()).isNull();
    }

    @Test
    void repliesWhenSelectionUnresolvable() {
        OrchestrationResult result = select(List.of(H1, H2), "bilmiyorum ki");
        assertThat(result.redirectToReservation()).isFalse();
        assertThat(result.selectedProduct()).isNull();
    }

    @Test
    void outOfRangeNumberIsUnresolved() {
        assertThat(select(List.of(H1, H2), "9").selectedProduct()).isNull();
    }
}
