package com.paximum.paxassist.orchestrator.clarify;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.paximum.paxassist.chat.domain.ChatSession;

import static org.assertj.core.api.Assertions.assertThat;

class ClarificationComposerTest {

    private final ClarificationCatalog catalog = new ClarificationCatalog();
    private final ClarificationComposer composer =
            new ClarificationComposer(catalog, new NoPreferenceDetector());

    private ChatSession sessionAfterAssistantSaid(String... assistantReplies) {
        ChatSession session = new ChatSession("s1");
        for (String reply : assistantReplies) {
            session.addMessage("user", "…");
            session.addMessage("assistant", reply);
        }
        return session;
    }

    @Test
    void firstAskUsesThePlainCatalogQuestion() {
        String message = composer.forFlight(new ChatSession("s1"), "sakıza uçuş var mı", List.of("origin"));
        assertThat(message).isEqualTo(catalog.questionForFlight(List.of("origin")));
    }

    @Test
    void noPreferenceOnOrigin_offersConcreteCitiesInsteadOfRepeating() {
        // The reported bug: "Nereden kalkmak istersin?" → "fark etmez" → the same question again.
        ChatSession session = sessionAfterAssistantSaid("Nereden kalkmak istersin?");
        String message = composer.forFlight(session, "fark etmez", List.of("origin"));

        assertThat(message).isNotEqualTo("Nereden kalkmak istersin?");
        assertThat(message).contains("İstanbul").contains("Ankara").contains("İzmir");
    }

    @Test
    void noPreferenceOnFlightDestination_offersCities() {
        String message = composer.forFlight(new ChatSession("s1"), "sen seç", List.of("destination"));
        assertThat(message).contains("Antalya");
    }

    @Test
    void noPreferenceOnHotelDestination_offersCities() {
        String message = composer.forHotel(new ChatSession("s1"), "farketmez", List.of("destination"));
        assertThat(message).contains("Antalya").contains("Bodrum");
    }

    @Test
    void noPreferenceOnADateStillAsks_butDoesNotOfferAFabricatedDate() {
        // No date may be suggested (that would imply availability we have not checked), but the reply
        // must still acknowledge the answer rather than repeat the question verbatim.
        String plain = catalog.questionForFlight(List.of("departDate"));
        String message = composer.forFlight(new ChatSession("s1"), "fark etmez", List.of("departDate"));

        assertThat(message).isNotEqualTo(plain);
        assertThat(message).contains(plain);
    }

    @Test
    void repeatedQuestion_isRephrasedRatherThanRepeatedVerbatim() {
        String plain = catalog.questionForFlight(List.of("origin"));
        ChatSession session = sessionAfterAssistantSaid(plain);

        String message = composer.forFlight(session, "sakızdan", List.of("origin"));

        assertThat(message).isNotEqualTo(plain);
        assertThat(message).contains("Bunu tam olarak alamadım");
    }

    @Test
    void repeatDetectionIgnoresACarryOverSuffixOnThePreviousReply() {
        String plain = catalog.questionForFlight(List.of("origin"));
        ChatSession session = sessionAfterAssistantSaid(plain + " (2 yetişkin bilgisini taşıdım)");

        assertThat(composer.forFlight(session, "hmm", List.of("origin"))).isNotEqualTo(plain);
    }

    @Test
    void aDifferentPreviousQuestion_doesNotCountAsARepeat() {
        ChatSession session = sessionAfterAssistantSaid(catalog.questionForFlight(List.of("destination")));
        assertThat(composer.forFlight(session, "Ankara", List.of("origin")))
                .isEqualTo(catalog.questionForFlight(List.of("origin")));
    }

    @Test
    void emptyMissingFields_fallsBackToTheCatalogFallback() {
        assertThat(composer.forFlight(new ChatSession("s1"), "merhaba", List.of()))
                .isEqualTo(catalog.questionForFlight(List.of()));
    }
}
