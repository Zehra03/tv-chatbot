package com.paximum.paxassist.hotel.dto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The party→rooms split that feeds TourVisio's {@code roomCriteria}. The search previously sent a
 * single room holding everyone, so a multi-room search was quoted a single room's price.
 */
class RoomPartyTest {

    @Test
    @DisplayName("one room keeps the whole party together (the old behaviour, now only when asked for)")
    void singleRoomHoldsEveryone() {
        List<RoomParty> parties = RoomParty.distribute(4, 1, List.of(6, 9));

        assertThat(parties).hasSize(1);
        assertThat(parties.get(0).adult()).isEqualTo(4);
        assertThat(parties.get(0).childAges()).containsExactly(6, 9);
    }

    @Test
    @DisplayName("4 adults over 2 rooms → two rooms of 2, not one room of 4")
    void splitsAdultsEvenly() {
        List<RoomParty> parties = RoomParty.distribute(4, 2, List.of());

        assertThat(parties).hasSize(2);
        assertThat(parties).allSatisfy(p -> assertThat(p.adult()).isEqualTo(2));
    }

    @Test
    @DisplayName("odd adults: remainder goes to the first rooms, every room keeps an adult")
    void spreadsRemainder() {
        assertThat(RoomParty.distribute(3, 2, List.of()))
                .extracting(RoomParty::adult)
                .containsExactly(2, 1);

        assertThat(RoomParty.distribute(5, 3, List.of()))
                .extracting(RoomParty::adult)
                .containsExactly(2, 2, 1);
    }

    @Test
    @DisplayName("children are dealt round-robin, not piled into room one")
    void dealsChildrenRoundRobin() {
        List<RoomParty> parties = RoomParty.distribute(2, 2, List.of(6, 9));

        assertThat(parties).hasSize(2);
        assertThat(parties.get(0).childAges()).containsExactly(6);
        assertThat(parties.get(1).childAges()).containsExactly(9);
    }

    @Test
    @DisplayName("never more rooms than adults — each room needs at least one adult")
    void clampsRoomsToAdults() {
        List<RoomParty> parties = RoomParty.distribute(2, 5, List.of());

        assertThat(parties).hasSize(2);
        assertThat(parties).allSatisfy(p -> assertThat(p.adult()).isEqualTo(1));
    }

    @Test
    @DisplayName("degenerate input still yields one usable room rather than throwing")
    void handlesMissingCounts() {
        assertThat(RoomParty.distribute(null, null, null)).hasSize(1);
        assertThat(RoomParty.distribute(0, 0, null)).hasSize(1);
        assertThat(RoomParty.distribute(null, null, null).get(0).adult()).isEqualTo(1);
    }

    @Test
    @DisplayName("every adult is placed exactly once, whatever the split")
    void conservesAdults() {
        for (int adults = 1; adults <= 9; adults++) {
            for (int rooms = 1; rooms <= adults; rooms++) {
                int placed = RoomParty.distribute(adults, rooms, List.of()).stream()
                        .mapToInt(RoomParty::adult)
                        .sum();
                assertThat(placed)
                        .as("%d adults over %d rooms", adults, rooms)
                        .isEqualTo(adults);
            }
        }
    }

    @Test
    @DisplayName("every child is placed exactly once")
    void conservesChildren() {
        List<Integer> kids = List.of(3, 6, 9, 12, 15);
        List<RoomParty> parties = RoomParty.distribute(3, 3, kids);

        assertThat(parties.stream().flatMap(p -> p.childAges().stream()))
                .containsExactlyInAnyOrderElementsOf(kids);
    }
}
