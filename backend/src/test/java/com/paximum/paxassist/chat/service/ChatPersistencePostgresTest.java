package com.paximum.paxassist.chat.service;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import com.paximum.paxassist.chat.domain.ChatMessageEntity;
import com.paximum.paxassist.chat.domain.ChatSession;
import com.paximum.paxassist.chat.domain.ChatSessionEntity;
import com.paximum.paxassist.chat.repository.ChatSessionRepository;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The definitive PR5 verification: boots against a real Postgres where Flyway applies
 * V1__initial_schema.sql and Hibernate {@code ddl-auto=validate} checks every @Entity against it.
 * If the chat entities drift from the migration, the context fails to load and this test fails.
 * It then round-trips a session to confirm the JSONB mappings work end-to-end on Postgres.
 */
@SpringBootTest
@ActiveProfiles("mock-ai")
@Testcontainers
class ChatPersistencePostgresTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Container
    static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
    }

    @Autowired
    private ChatSessionStore store;
    @Autowired
    private ChatSessionRepository repository;

    @Test
    void flywaySchemaValidatesAndSessionRoundTripsOnPostgres() {
        // Reaching this point means Flyway ran V1 and ddl-auto=validate passed against it.
        ChatSession session = store.getOrCreate(null, null);
        session.getAccumulatedCriteria().put("location", "Antalya");
        session.addMessage("user", "Antalya otel");
        session.addMessage("assistant", "Hangi tarihte?");
        store.save(session);

        ChatSessionEntity entity = repository.findByIdWithMessages(Long.valueOf(session.getId())).orElseThrow();
        assertThat(entity.getAccumulatedCriteria()).containsEntry("location", "Antalya");
        assertThat(entity.getMessages()).extracting(ChatMessageEntity::getRole)
                .containsExactly("user", "assistant");
    }
}
