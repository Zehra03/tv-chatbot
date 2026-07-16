package com.paximum.paxassist.chat.domain;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ChatSessionSwitchDomainTest {

    private ChatSession hotelSessionWithResults() {
        ChatSession session = new ChatSession("s1");
        Map<String, Object> criteria = new HashMap<>();
        criteria.put("location", "Antalya");
        criteria.put("checkIn", "2026-08-01");
        criteria.put("nights", 5);
        criteria.put("boardType", "AI");
        criteria.put("adults", 2);
        criteria.put("children", 1);
        criteria.put("childAges", List.of(7));
        criteria.put("nationality", "TR");
        criteria.put("currency", "TRY");
        session.setAccumulatedCriteria(criteria);
        session.setActiveDomain("HOTEL");
        session.setLastApiResultCards(List.of("hotel-1", "hotel-2"));
        session.setLastResultCards(List.of("hotel-1"));
        return session;
    }

    @Test
    void dropsThePreviousDomainsSearchCriteria() {
        ChatSession session = hotelSessionWithResults();

        session.switchDomain("FLIGHT");

        assertThat(session.getAccumulatedCriteria())
                .doesNotContainKeys("location", "checkIn", "nights", "boardType");
    }

    /** Who is travelling does not change when the user switches from hotels to flights. */
    @Test
    void keepsTheTravellersOwnDetails() {
        ChatSession session = hotelSessionWithResults();

        session.switchDomain("FLIGHT");

        assertThat(session.getAccumulatedCriteria())
                .containsEntry("adults", 2)
                .containsEntry("children", 1)
                .containsEntry("childAges", List.of(7))
                .containsEntry("nationality", "TR")
                .containsEntry("currency", "TRY");
    }

    /**
     * The bug this guards: cards used to survive the switch, so "en ucuzu seç" right after
     * "vazgeçtim, uçak arıyorum" selected a hotel from the old list.
     */
    @Test
    void dropsThePreviousDomainsResultCards() {
        ChatSession session = hotelSessionWithResults();

        session.switchDomain("FLIGHT");

        assertThat(session.getLastResultCards()).isEmpty();
        assertThat(session.getLastApiResultCards()).isEmpty();
        assertThat(session.getActiveDomain()).isEqualTo("FLIGHT");
    }

    @Test
    void toleratesASessionWithNoCriteriaYet() {
        ChatSession session = new ChatSession("s1");

        session.switchDomain("HOTEL");

        assertThat(session.getActiveDomain()).isEqualTo("HOTEL");
        assertThat(session.getAccumulatedCriteria()).isEmpty();
    }
}
