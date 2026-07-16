package com.paximum.paxassist.flight.infrastructure.dto.response;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * One itinerary from a price search — <b>a single leg</b>, not a whole trip: a round-trip search
 * returns the outbound and the return as separate results (their {@code items} carry {@code route}
 * 1 and 2 respectively), each priced by its own {@link #offers()}. Pairing them back into a trip is
 * the mapper's job, via the offers' group keys.
 *
 * <p>{@link #offer()} is the legacy single-offer field; current responses send {@link #offers()}.
 * Read them through {@link #allOffers()} rather than either directly.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TourVisioFlightResult(
        String id,
        List<TourVisioFlightItem> items,
        TourVisioOffer offer,
        List<TourVisioOffer> offers) {

    /** The fares for this leg, from whichever of the two response shapes the provider sent. */
    public List<TourVisioOffer> allOffers() {
        if (offers != null && !offers.isEmpty()) {
            return offers;
        }
        return offer != null ? List.of(offer) : List.of();
    }
}
