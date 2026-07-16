package com.paximum.paxassist.flight.infrastructure.dto.response;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * One purchasable fare for a single leg (TourVisio prices the outbound and the return separately;
 * see {@link TourVisioFlightResult}). {@code price} is the total for the searched party — not a
 * per-adult figure ({@code singleAdultPrice} is that, and is deprecated).
 *
 * <p>Two response shapes are covered on purpose:
 * <ul>
 *   <li>the current one ({@code flightResponseListType} 3), where the booking token lives in
 *       {@link #offerIds()} — one per {@link #groupKeys() group key};</li>
 *   <li>the legacy one, which carries a single flat {@link #offerId()} and no groups.</li>
 * </ul>
 * {@link #offerIdFor} hides that difference from callers.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TourVisioOffer(
        String offerId,
        List<TourVisioOfferId> offerIds,
        List<String> groupKeys,
        Boolean isPackageOffer,
        TourVisioPrice price) {

    /** True when the provider bundles the round trip, which changes how the total is computed. */
    public boolean packaged() {
        return Boolean.TRUE.equals(isPackageOffer);
    }

    /**
     * The booking token for this offer once the outbound and the return have agreed on a group key.
     *
     * @param groupKey the agreed key, or null for a leg that stands on its own (one-way / legacy)
     * @return the token, or null when this offer has none for that key
     */
    public String offerIdFor(String groupKey) {
        if (offerIds == null || offerIds.isEmpty()) {
            return offerId;
        }
        if (groupKey == null) {
            return offerIds.get(0).offerId();
        }
        for (TourVisioOfferId candidate : offerIds) {
            if (groupKey.equals(candidate.groupKey())) {
                return candidate.offerId();
            }
        }
        return null;
    }
}
