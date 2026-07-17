package com.paximum.paxassist.orchestrator.intent;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.paximum.paxassist.flight.domain.FlightProduct;

/**
 * Views over the round-trip combinations a flight search produced, for the two-step choice the chat
 * offers: first which outbound to fly, then which return to fly with it.
 *
 * <p>Both steps are filters over real, whole combinations — never a leg sold on its own. A price
 * shown at either step is the provider's total for the trip that combination buys.
 */
final class RoundTripOptions {

    private RoundTripOptions() {
    }

    /**
     * True for a card that is a round trip (both legs), as opposed to a hotel or a one-way.
     *
     * <p>Keyed on the return leg, not on a second booking token: when the provider sells a trip as
     * one result, a single token buys both legs and there is no return token to look for.
     */
    static boolean isRoundTrip(Object card) {
        return card instanceof FlightProduct flight && flight.getReturnDepartTime() != null;
    }

    /**
     * One card per outbound — the cheapest way to fly it — so the first list is about outbounds
     * rather than every combination at once. Provider order is kept; the returns are the next step.
     */
    static List<Object> outboundChoices(List<Object> combinations) {
        Map<String, FlightProduct> cheapestPerOutbound = new LinkedHashMap<>();
        for (Object card : combinations) {
            if (!(card instanceof FlightProduct flight) || flight.getOutboundLegId() == null) {
                continue;
            }
            cheapestPerOutbound.merge(flight.getOutboundLegId(), flight,
                    (existing, candidate) -> cheaper(existing, candidate) ? existing : candidate);
        }
        return new ArrayList<>(cheapestPerOutbound.values());
    }

    /** The returns that may be flown with this outbound, cheapest trip first. */
    static List<Object> returnsFor(List<Object> combinations, String outboundLegId) {
        return combinations.stream()
                .filter(card -> card instanceof FlightProduct flight
                        && outboundLegId != null
                        && outboundLegId.equals(flight.getOutboundLegId()))
                .sorted(Comparator.comparing(card -> priceOrMax((FlightProduct) card)))
                .toList();
    }

    private static boolean cheaper(FlightProduct existing, FlightProduct candidate) {
        return priceOrMax(existing).compareTo(priceOrMax(candidate)) <= 0;
    }

    /** An unpriced combination must never win a "cheapest" comparison. */
    private static BigDecimal priceOrMax(FlightProduct flight) {
        return flight.getPrice() != null ? flight.getPrice() : new BigDecimal(Long.MAX_VALUE);
    }
}
