package com.paximum.paxassist.reservation.service;

/**
 * Outcome of {@code previewReservation}. Distinct states, because a preview can now fail: it asks
 * TourVisio to price the offer, and the offer may be gone or the provider unreachable.
 *
 * <p>K21 requires the price and availability to be re-validated when the user moves to reservation.
 * Before, the preview was assembled purely from client input, so a sold-out room or a changed price
 * only surfaced after the user pressed confirm — with the TourVisio transaction failing on an opaque
 * error. Now it surfaces while nothing is committed and the user can still decide.
 */
public sealed interface PreviewResult
        permits PreviewResult.Priced, PreviewResult.Unavailable, PreviewResult.ProviderUnavailable {

    /**
     * The offer is still bookable and has been re-priced. {@link ReservationPreview#priceChanged()}
     * tells the caller whether the amount moved since the search, in which case the summary carries
     * both amounts and the user is asked to accept the new one.
     */
    record Priced(ReservationPreview preview) implements PreviewResult {}

    /** TourVisio refused to price the offer — sold out, expired or otherwise no longer bookable. */
    record Unavailable(String providerMessage) implements PreviewResult {}

    /** TourVisio could not be reached, so availability could NOT be verified. Nothing is frozen. */
    record ProviderUnavailable(String description) implements PreviewResult {}
}
