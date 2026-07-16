package com.paximum.paxassist.chat.service;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import com.paximum.paxassist.chat.domain.ChatCaller;
import com.paximum.paxassist.chat.domain.ChatMessageEntity;
import com.paximum.paxassist.chat.domain.ChatSession;
import com.paximum.paxassist.chat.domain.ChatSessionEntity;
import com.paximum.paxassist.chat.repository.ChatSessionRepository;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Store round-trip on H2. Profiles {@code loadtest,mock-ai} give ddl-auto=create-drop (so the chat
 * tables are built from the entities — also proving the JSON column mappings are valid on H2) plus a
 * mock ChatModel; {@code mock} is intentionally omitted because its {@code ddl-auto=none} would
 * override create-drop and leave the DB empty. No external services needed.
 */
@SpringBootTest
@ActiveProfiles({"loadtest", "mock-ai"})
class JpaChatSessionStoreTest {

    @Autowired
    private ChatSessionStore store;
    @Autowired
    private ChatSessionRepository repository;

    @Test
    void mintsPersistsAndReloadsTranscriptAndCriteria() {
        ChatSession session = store.getOrCreate(null, ChatCaller.ANONYMOUS);
        assertThat(session.getId()).isNotBlank();

        session.getAccumulatedCriteria().put("location", "Antalya");
        session.addMessage("user", "Antalya otel");
        session.addMessage("assistant", "Hangi tarihte?");
        store.save(session);

        ChatSessionEntity entity = repository.findByIdWithMessages(Long.valueOf(session.getId())).orElseThrow();
        assertThat(entity.getAccumulatedCriteria()).containsEntry("location", "Antalya");
        assertThat(entity.getMessages()).extracting(ChatMessageEntity::getRole)
                .containsExactly("user", "assistant");
        assertThat(entity.getMessages()).extracting(ChatMessageEntity::getContent)
                .containsExactly("Antalya otel", "Hangi tarihte?");
    }

    @Test
    void appendsOnlyNewMessagesOnSubsequentSaves() {
        ChatSession session = store.getOrCreate(null, ChatCaller.ANONYMOUS);
        session.addMessage("user", "ilk");
        session.addMessage("assistant", "cevap1");
        store.save(session);

        session.addMessage("user", "ikinci");
        session.addMessage("assistant", "cevap2");
        store.save(session);

        ChatSessionEntity entity = repository.findByIdWithMessages(Long.valueOf(session.getId())).orElseThrow();
        assertThat(entity.getMessages()).hasSize(4); // no duplicated rows across saves
    }

    @Test
    void unknownIdMintsAFreshPersistedSession() {
        ChatSession session = store.getOrCreate("999999", ChatCaller.ANONYMOUS);
        assertThat(session.getId()).isNotEqualTo("999999");
        assertThat(repository.existsById(Long.valueOf(session.getId()))).isTrue();
    }
}
