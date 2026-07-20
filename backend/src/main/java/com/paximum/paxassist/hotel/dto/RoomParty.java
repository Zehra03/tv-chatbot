package com.paximum.paxassist.hotel.dto;

import java.util.ArrayList;
import java.util.List;

/**
 * One room's occupancy for a TourVisio {@code roomCriteria} entry.
 *
 * <p>Exists because the search used to send {@code roomCriteria = List.of(oneRoom)} with EVERY
 * adult in that single room, no matter how many rooms the guest asked for. A "4 adults, 2 rooms"
 * search was therefore priced as one quad room, and the offer the guest saw (and booked, with
 * {@code rooms: 2} recorded on the reservation) did not correspond to the party they searched for.
 * That collides with the project rule that prices are never invented.
 *
 * @param adult     adults sleeping in this room (always >= 1)
 * @param childAges ages of the children in this room; empty when none
 */
public record RoomParty(int adult, List<Integer> childAges) {

    public RoomParty {
        childAges = childAges == null ? List.of() : List.copyOf(childAges);
    }

    /**
     * Splits a party across {@code rooms} rooms.
     *
     * <p>Adults are spread as evenly as possible and the remainder goes to the first rooms, so
     * 3 adults in 2 rooms is 2+1 rather than 3+0 — every room must hold at least one adult, which
     * is also the rule the frontend and {@code validatePreviewCommand} enforce (rooms <= adults).
     *
     * <p>Children are dealt round-robin rather than piled into the first room: a family of 2 adults
     * + 2 children in 2 rooms reads as two 1+1 rooms, which is what such a party normally books.
     * Loading room one with both children (1 adult + 2 children) would often exceed that room's
     * occupancy and make TourVisio return nothing at all.
     *
     * <p><b>Unverified against live TourVisio:</b> the even/round-robin policy is the sensible
     * default, but which allocation a given hotel actually prices is a provider/product question.
     * Worth confirming against a real multi-room search before relying on it commercially.
     *
     * @param adults    total adults (values < 1 are treated as 1 — the search itself rejects 0)
     * @param rooms     requested rooms; clamped to [1, adults]
     * @param childAges all children's ages, or null/empty
     */
    public static List<RoomParty> distribute(Integer adults, Integer rooms, List<Integer> childAges) {
        int totalAdults = (adults == null || adults < 1) ? 1 : adults;
        int roomCount = (rooms == null || rooms < 1) ? 1 : Math.min(rooms, totalAdults);

        List<List<Integer>> childrenPerRoom = new ArrayList<>();
        for (int i = 0; i < roomCount; i++) {
            childrenPerRoom.add(new ArrayList<>());
        }
        if (childAges != null) {
            int i = 0;
            for (Integer age : childAges) {
                if (age == null) {
                    continue;
                }
                childrenPerRoom.get(i % roomCount).add(age);
                i++;
            }
        }

        int base = totalAdults / roomCount;
        int remainder = totalAdults % roomCount;

        List<RoomParty> parties = new ArrayList<>(roomCount);
        for (int i = 0; i < roomCount; i++) {
            parties.add(new RoomParty(base + (i < remainder ? 1 : 0), childrenPerRoom.get(i)));
        }
        return List.copyOf(parties);
    }
}
