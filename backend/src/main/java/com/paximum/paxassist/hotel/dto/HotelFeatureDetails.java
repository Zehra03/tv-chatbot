package com.paximum.paxassist.hotel.dto;

import java.util.List;

/**
 * The standard, frontend-facing model for a hotel's bookable characteristics, built on the detail
 * screen (where a full {@code GetOfferDetails}/{@code GetProductInfo} payload is available).
 *
 * <p>The three sections are intentionally kept apart because their TourVisio sources and reliability
 * differ (see the card's "response modeli — iki katman ayrı tutulacak"):
 * <ul>
 *   <li>{@code hotelFeatures} — hotel/room facility ids &amp; names grouped via {@code facility-mapping.json}.</li>
 *   <li>{@code boardOptions}  — normalized {@code boardName} free text (e.g. {@code ALL_INCLUSIVE}).</li>
 *   <li>{@code themeFilters}  — {@code hotel.themes[]} names; an empty array when the provider sent none
 *       (never an error).</li>
 * </ul>
 *
 * <p><b>Note:</b> sea view is deliberately absent — TourVisio has no structured field for it, so
 * fabricating one would misrepresent provider data.
 */
public record HotelFeatureDetails(
    HotelFeaturesDto hotelFeatures,
    List<String> boardOptions,
    List<String> themeFilters
) {
    public HotelFeatureDetails {
        hotelFeatures = hotelFeatures == null ? new HotelFeaturesDto(false, List.of()) : hotelFeatures;
        boardOptions = boardOptions == null ? List.of() : List.copyOf(boardOptions);
        themeFilters = themeFilters == null ? List.of() : List.copyOf(themeFilters);
    }
}
