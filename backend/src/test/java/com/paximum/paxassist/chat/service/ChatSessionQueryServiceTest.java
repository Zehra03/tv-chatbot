package com.paximum.paxassist.chat.service;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import com.paximum.paxassist.chat.domain.ChatCaller;
import com.paximum.paxassist.chat.domain.ChatSession;
import com.paximum.paxassist.chat.dto.ChatSessionDto;
import com.paximum.paxassist.chat.dto.ChatSessionSummaryDto;
import com.paximum.paxassist.hotel.HotelProduct;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Data-layer slice for the chat read side. Uses {@code @DataJpaTest} (embedded H2, entities +
 * repositories only) so it exercises the real {@code findSummariesByUserId} query, the JSONB
 * result-card round-trip, and user scoping WITHOUT loading the web autoconfigure — which keeps it
 * green regardless of the unrelated mcp-server-webmvc web-context conflict on this branch. The
 * store + query service + mapper are the collaborators under test, imported into the slice.
 */
@DataJpaTest
// loadtest profile = H2 + Flyway off + ddl-auto=create-drop (the V1 migration is Postgres-only);
// replace=NONE keeps that profile's H2 datasource instead of a fresh embedded one.
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles({"loadtest", "mock-ai"})
@Import({ChatViewMapper.class, JpaChatSessionStore.class, ChatSessionQueryService.class})
class ChatSessionQueryServiceTest {

    private static final long OWNER = 7L;
    private static final long OTHER = 8L;

    @Autowired
    private ChatSessionStore store;
    @Autowired
    private ChatSessionQueryService queryService;

    @Test
    void listsSummariesAndLoadsTranscriptForOwner() {
        ChatSession session = store.getOrCreate(null, ChatCaller.authenticated(OWNER));
        session.getAccumulatedCriteria().put("location", "Antalya");
        session.addMessage("user", "Antalya otel bul");
        session.setLastResultCards(List.of(
                new HotelProduct("H1", "Rixos", "Antalya", 5, new BigDecimal("1500"), "EUR", "AI", true)));
        session.addMessage("assistant", "1 otel buldum:");
        store.save(session);

        List<ChatSessionSummaryDto> summaries = queryService.listSummaries(ChatCaller.authenticated(OWNER));
        assertThat(summaries).hasSize(1);
        assertThat(summaries.get(0).id()).isEqualTo(session.getId());
        assertThat(summaries.get(0).title()).isEqualTo("Antalya otel bul"); // derived from first user message
        assertThat(summaries.get(0).messageCount()).isEqualTo(2);

        ChatSessionDto dto = queryService.getSession(session.getId(), ChatCaller.authenticated(OWNER)).orElseThrow();
        assertThat(dto.messages()).hasSize(2);
        assertThat(dto.messages().get(0).role()).isEqualTo("user");
        assertThat(dto.messages().get(1).cards()).hasSize(1);
        assertThat(dto.messages().get(1).cards().get(0).productType()).isEqualTo("hotel"); // jsonb-restored
        assertThat(dto.accumulatedCriteria().intent()).isEqualTo("hotel");
        assertThat(dto.accumulatedCriteria().criteria()).containsEntry("destination", "Antalya");
    }

    @Test
    void doesNotLeakSessionsAcrossUsers() {
        ChatSession session = store.getOrCreate(null, ChatCaller.authenticated(OWNER));
        session.addMessage("user", "gizli");
        session.addMessage("assistant", "tamam");
        store.save(session);

        assertThat(queryService.listSummaries(ChatCaller.authenticated(OTHER))).isEmpty();
        assertThat(queryService.getSession(session.getId(), ChatCaller.authenticated(OTHER))).isEmpty();
    }
}
