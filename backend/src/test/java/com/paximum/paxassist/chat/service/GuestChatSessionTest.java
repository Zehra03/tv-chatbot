package com.paximum.paxassist.chat.service;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import com.paximum.paxassist.chat.domain.ChatCaller;
import com.paximum.paxassist.chat.domain.ChatSession;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guest (unauthenticated) session ownership. Guests are scoped by an opaque {@code guest_token}
 * exactly the way users are scoped by {@code user_id}, so this mirrors {@link ChatSessionQueryServiceTest}
 * on the guest path: a guest continues only their own conversation, and can never list/load/continue/
 * delete another guest's — or a user's — session (the sequential id is useless without the token).
 * Same reliable {@code @DataJpaTest} slice (embedded H2, no external services).
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles({"loadtest", "mock-ai"})
@Import({ChatViewMapper.class, JpaChatSessionStore.class, ChatSessionQueryService.class})
class GuestChatSessionTest {

    private static final ChatCaller GUEST_A = ChatCaller.guest("guest-aaaaaaaa");
    private static final ChatCaller GUEST_B = ChatCaller.guest("guest-bbbbbbbb");
    private static final ChatCaller USER = ChatCaller.authenticated(7L);

    @Autowired
    private ChatSessionStore store;
    @Autowired
    private ChatSessionQueryService queryService;

    @Test
    void guestContinuesOwnSessionAcrossTurns_andSeesItInHistory() {
        ChatSession first = store.getOrCreate(null, GUEST_A);
        first.getAccumulatedCriteria().put("location", "Antalya");
        first.addMessage("user", "Antalya otel");
        first.addMessage("assistant", "Hangi tarihte?");
        store.save(first);

        // Same guest presenting the returned id continues the same session (slot-filling preserved).
        ChatSession again = store.getOrCreate(first.getId(), GUEST_A);
        assertThat(again.getId()).isEqualTo(first.getId());
        assertThat(again.getAccumulatedCriteria()).containsEntry("location", "Antalya");

        // And it shows up in the guest's own history.
        assertThat(queryService.listSummaries(GUEST_A)).hasSize(1);
        assertThat(queryService.getSession(first.getId(), GUEST_A)).isPresent();
    }

    @Test
    void guestCannotSee_continue_orDeleteAnotherGuestsSession() {
        ChatSession a = store.getOrCreate(null, GUEST_A);
        a.addMessage("user", "gizli");
        a.addMessage("assistant", "tamam");
        store.save(a);

        // Guest B: empty history, no read, and presenting A's id mints a fresh session (never A's).
        assertThat(queryService.listSummaries(GUEST_B)).isEmpty();
        assertThat(queryService.getSession(a.getId(), GUEST_B)).isEmpty();
        assertThat(store.getOrCreate(a.getId(), GUEST_B).getId()).isNotEqualTo(a.getId());
        assertThat(store.delete(a.getId(), GUEST_B)).isFalse();

        // A is untouched by B's probing.
        assertThat(queryService.getSession(a.getId(), GUEST_A)).isPresent();
    }

    @Test
    void guestAndUserSessionsNeverCross() {
        ChatSession guestSession = store.getOrCreate(null, GUEST_A);
        guestSession.addMessage("user", "misafir");
        store.save(guestSession);

        // A logged-in user cannot load or list the guest's session.
        assertThat(queryService.getSession(guestSession.getId(), USER)).isEmpty();
        assertThat(queryService.listSummaries(USER)).isEmpty();

        // And a guest cannot load or list a user's session.
        ChatSession userSession = store.getOrCreate(null, USER);
        userSession.addMessage("user", "uye");
        store.save(userSession);
        assertThat(queryService.getSession(userSession.getId(), GUEST_A)).isEmpty();
        assertThat(queryService.listSummaries(GUEST_A)).hasSize(1); // only the guest's own row
    }

    @Test
    void guestOwnerCanDeleteOwnSession() {
        ChatSession a = store.getOrCreate(null, GUEST_A);
        a.addMessage("user", "sil beni");
        store.save(a);

        assertThat(store.delete(a.getId(), GUEST_A)).isTrue();
        assertThat(queryService.getSession(a.getId(), GUEST_A)).isEmpty();
    }
}
