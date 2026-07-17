package com.paximum.paxassist.orchestrator.intent;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.paximum.paxassist.ai.IntentType;
import com.paximum.paxassist.ai.SlotCriteria;
import com.paximum.paxassist.chat.domain.ChatSession;
import com.paximum.paxassist.flight.domain.FlightProduct;
import com.paximum.paxassist.orchestrator.OrchestrationContext;
import com.paximum.paxassist.orchestrator.OrchestrationResult;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The two-step round-trip choice: the user picks an outbound, then a return, and only then goes to
 * the reservation — with the booking tokens of the exact combination they chose.
 */
class RoundTripSelectionTest {

    private final SelectHandler handler = new SelectHandler();

    /** One combination: fly out on {@code outboundLegId}, back on {@code returnLegId}. */
    private static FlightProduct combination(String outboundLegId, String returnLegId, String price) {
        return FlightProduct.builder()
                .id(outboundLegId + "::" + returnLegId)
                .outboundLegId(outboundLegId)
                .returnLegId(returnLegId)
                .offerId("offer-" + outboundLegId)
                .returnOfferId("offer-" + returnLegId)
                .airline("TK")
                .origin("IST")
                .destination("AYT")
                // A return leg the user flies home on is what makes this a round trip, so a
                // combination always has its times — the mapper cannot produce one without them.
                .returnDepartTime(Instant.parse("2026-08-27T04:50:00Z"))
                .returnArriveTime(Instant.parse("2026-08-27T06:00:00Z"))
                .price(new BigDecimal(price))
                .currency("TRY")
                .build();
    }

    /** Two outbounds, two returns each: a real choice at both steps. */
    private static List<Object> combinations() {
        return List.of(
                combination("out-1", "in-1", "1000"),
                combination("out-1", "in-2", "1200"),
                combination("out-2", "in-1", "1500"),
                combination("out-2", "in-2", "1700"));
    }

    private static ChatSession sessionShowingOutbounds() {
        ChatSession session = new ChatSession("s1");
        session.setActiveDomain("FLIGHT");
        session.setRoundTripOptions(combinations());
        session.setLastResultCards(RoundTripOptions.outboundChoices(combinations()));
        return session;
    }

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private static OrchestrationContext select(ChatSession session, String reference) {
        SlotCriteria criteria =
                OBJECT_MAPPER.convertValue(Map.of("selectionReference", reference), SlotCriteria.class);
        return new OrchestrationContext(session, reference, IntentType.SELECT, criteria);
    }

    @Test
    void theOutboundListShowsEachOutboundOnce_atItsCheapestTotal() {
        List<Object> choices = RoundTripOptions.outboundChoices(combinations());

        assertThat(choices).hasSize(2);
        assertThat(choices).extracting(card -> ((FlightProduct) card).getOutboundLegId())
                .containsExactly("out-1", "out-2");
        assertThat(((FlightProduct) choices.get(0)).getPrice()).isEqualByComparingTo(new BigDecimal("1000"));
        assertThat(((FlightProduct) choices.get(1)).getPrice()).isEqualByComparingTo(new BigDecimal("1500"));
    }

    /**
     * A trip the provider sells as one ticket (one token, no return token) has no leg choice to
     * make: picking it goes to the reservation, which is what the card's Seç button does anyway.
     * Offering a second step here promised one the flow could not deliver.
     */
    @Test
    void pickingASingleTicketTripGoesStraightToTheReservation() {
        FlightProduct wholeTrip = FlightProduct.builder()
                .id("trip-1")
                .outboundLegId("VF4009@2026-07-18T21:20:00")
                .returnLegId("trip-1")
                .offerId("offer-trip-1")
                .returnOfferId(null) // one token buys both legs
                .airline("VF")
                .origin("AYT")
                .destination("ADB")
                .returnDepartTime(Instant.parse("2026-07-21T15:00:00Z"))
                .returnArriveTime(Instant.parse("2026-07-21T16:10:00Z"))
                .price(new BigDecimal("3817"))
                .currency("TRY")
                .build();
        ChatSession session = new ChatSession("s1");
        session.setActiveDomain("FLIGHT");
        session.setLastResultCards(List.of(wholeTrip));

        OrchestrationResult result = handler.handle(select(session, "1"));

        assertThat(result.redirectToReservation()).isTrue();
        assertThat(result.reply()).doesNotContain("dönüşünü seç");
        assertThat(session.getPendingOutboundLegId()).isNull();
    }

    @Test
    void pickingAnOutboundOffersTheReturnsThatFlyWithIt_insteadOfBooking() {
        ChatSession session = sessionShowingOutbounds();

        OrchestrationResult result = handler.handle(select(session, "1"));

        assertThat(result.redirectToReservation()).isFalse();
        assertThat(result.reply()).contains("dönüşünü seç");
        assertThat(result.cards()).hasSize(2);
        assertThat(result.cards()).extracting(card -> ((FlightProduct) card).getReturnLegId())
                .containsExactly("in-1", "in-2"); // cheapest trip first
        assertThat(session.getPendingOutboundLegId()).isEqualTo("out-1");
    }

    @Test
    void theReturnsOfferedAreOnlyThoseOfTheChosenOutbound() {
        ChatSession session = sessionShowingOutbounds();

        OrchestrationResult result = handler.handle(select(session, "2"));

        assertThat(result.cards()).allSatisfy(card ->
                assertThat(((FlightProduct) card).getOutboundLegId()).isEqualTo("out-2"));
        assertThat(session.getPendingOutboundLegId()).isEqualTo("out-2");
    }

    @Test
    void pickingTheReturnGoesToTheReservationWithBothTokensOfThatCombination() {
        ChatSession session = sessionShowingOutbounds();
        handler.handle(select(session, "1")); // outbound out-1

        OrchestrationResult result = handler.handle(select(session, "2")); // its second return: in-2

        assertThat(result.redirectToReservation()).isTrue();
        FlightProduct booked = (FlightProduct) result.selectedProduct();
        assertThat(booked.getOutboundLegId()).isEqualTo("out-1");
        assertThat(booked.getReturnLegId()).isEqualTo("in-2");
        assertThat(booked.getOfferId()).isEqualTo("offer-out-1");
        assertThat(booked.getReturnOfferId()).isEqualTo("offer-in-2");
        assertThat(booked.getPrice()).isEqualByComparingTo(new BigDecimal("1200"));
    }

    /** A step that offers a single option is a click for nothing. */
    @Test
    void goesStraightToTheReservationWhenTheOutboundHasOnlyOneReturn() {
        ChatSession session = new ChatSession("s1");
        session.setActiveDomain("FLIGHT");
        List<Object> single = List.of(combination("out-1", "in-1", "1000"));
        session.setRoundTripOptions(single);
        session.setLastResultCards(RoundTripOptions.outboundChoices(single));

        OrchestrationResult result = handler.handle(select(session, "1"));

        assertThat(result.redirectToReservation()).isTrue();
        assertThat(session.getPendingOutboundLegId()).isNull();
    }

    @Test
    void aOneWayFlightGoesStraightToTheReservation() {
        ChatSession session = new ChatSession("s1");
        session.setActiveDomain("FLIGHT");
        FlightProduct oneWay = FlightProduct.builder()
                .id("out-1").outboundLegId("out-1").offerId("offer-out-1")
                .airline("TK").origin("IST").destination("AYT")
                .price(new BigDecimal("800")).currency("TRY")
                .build();
        session.setLastResultCards(List.of(oneWay));

        OrchestrationResult result = handler.handle(select(session, "1"));

        assertThat(result.redirectToReservation()).isTrue();
        assertThat(session.getPendingOutboundLegId()).isNull();
    }
}
