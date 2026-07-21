package com.paximum.paxassist.orchestrator.slot;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The phrase that decides whether the chat asks for a return date. A miss here is not a cosmetic
 * one: the search falls back to one-way and the user is shown outbound-only results for a
 * round-trip request, with no question in between.
 */
class TripTypeDetectorTest {

    private final TripTypeDetector detector = new TripTypeDetector();

    @Test
    void readsRoundTripHoweverItIsSpelled() {
        for (String message : new String[] {
                "Gidiş-dönüş uçuş arıyorum",
                "gidiş dönüş bilet istiyorum",
                "GIDIŞ/DÖNÜŞ olsun",
                "gidis donus ucak lazim",
                "round trip bilet bakar mısın",
                "İstanbul Antalya, dönüşü de olsun"}) {
            assertThat(detector.detect(message))
                    .as(message)
                    .contains(SlotNormalizer.TRIP_TYPE_ROUND);
        }
    }

    @Test
    void readsOneWay() {
        for (String message : new String[] {
                "tek yön yeterli", "Tek yönlü olsun", "sadece gidiş", "one way", "tek yon"}) {
            assertThat(detector.detect(message)).as(message).contains(SlotNormalizer.TRIP_TYPE_ONE_WAY);
        }
    }

    @Test
    void correctionWins_soRetractingARoundTripIsNotReadAsAskingForOne() {
        // Both phrases appear; the user is correcting an earlier round-trip request.
        assertThat(detector.detect("gidiş-dönüş değil, tek yön olsun"))
                .contains(SlotNormalizer.TRIP_TYPE_ONE_WAY);
    }

    @Test
    void saysNothingWhenTheMessageSaysNothingAboutDirection() {
        // Most turns are like this — they must not overwrite what the user already stated.
        assertThat(detector.detect("İstanbul'dan Antalya'ya 2 kişi")).isEmpty();
        assertThat(detector.detect("")).isEmpty();
        assertThat(detector.detect(null)).isEmpty();
    }
}
