package com.paximum.paxassist.orchestrator.intent;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.paximum.paxassist.ai.SlotCriteria;

import static org.assertj.core.api.Assertions.assertThat;

class TravellerCarryOverTest {

    private static SlotCriteria withTravellers(Integer adults, Integer children, List<Integer> childAges) {
        SlotCriteria empty = SlotCriteria.empty();
        return new SlotCriteria(
                empty.location(), empty.checkIn(), empty.checkOut(), empty.nights(), empty.rooms(),
                empty.stars(), empty.maxStars(), empty.boardType(), empty.features(), empty.hotelMaxPrice(),
                empty.origin(), empty.destination(), empty.departureDate(), empty.returnDate(),
                empty.cabinClass(), empty.flightMaxPrice(), empty.directFlight(), empty.airline(),
                empty.departTimeRange(), empty.checkedBaggage(), empty.minCheckedBaggageKg(),
                empty.tripType(),
                adults, children, childAges,
                empty.nationality(), empty.currency(), empty.sortBy(), empty.limit(),
                empty.selectionReference());
    }

    @Test
    void tellsTheUserTheCountWasReusedAfterADomainSwitch() {
        String note = TravellerCarryOver.note(true, null, withTravellers(2, null, null));

        assertThat(note).contains("2 yetişkin").contains("Değiştirmek istersen");
    }

    @Test
    void countsChildrenFromTheirAges() {
        String note = TravellerCarryOver.note(true, null, withTravellers(2, 1, List.of(7)));

        assertThat(note).contains("2 yetişkin, 1 çocuk");
    }

    @Test
    void fallsBackToThePlainChildCountWhenNoAgesWereGiven() {
        String note = TravellerCarryOver.note(true, null, withTravellers(2, 3, null));

        assertThat(note).contains("2 yetişkin, 3 çocuk");
    }

    /** Nothing was carried over: the user gave the count in this very message. */
    @Test
    void staysSilentWhenTheUserRestatedTheCountThisTurn() {
        String note = TravellerCarryOver.note(true, withTravellers(3, null, null), withTravellers(3, null, null));

        assertThat(note).isEmpty();
    }

    @Test
    void staysSilentWithinTheSameDomain() {
        String note = TravellerCarryOver.note(false, null, withTravellers(2, null, null));

        assertThat(note).isEmpty();
    }

    @Test
    void staysSilentWhenThereIsNoCountToReuse() {
        assertThat(TravellerCarryOver.note(true, null, withTravellers(null, null, null))).isEmpty();
        assertThat(TravellerCarryOver.note(true, null, null)).isEmpty();
    }
}
