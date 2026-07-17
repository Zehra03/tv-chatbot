package com.paximum.paxassist.hotel.dto;

import java.util.List;

/**
 * Hotel-level feature summary for the detail screen. Kept deliberately separate from
 * {@code boardOptions} and {@code themeFilters} (see {@link HotelFeatureDetails}) because the three
 * come from different TourVisio sources with different reliability — never flatten them into one list.
 *
 * @param petFriendly     whether TourVisio reports the "Pets Allowed" facility (id 46). Absence of the
 *                        facility is reported as {@code false}, not "unknown" — we only ever assert
 *                        pet-friendliness from real provider data.
 * @param otherFacilities normalized facility-group labels (e.g. {@code "pool"}, {@code "spa_wellness"})
 *                        resolved from the real facility ids/names via {@code facility-mapping.json},
 *                        excluding {@code pet_friendly} (surfaced by {@link #petFriendly()}). Never null.
 */
public record HotelFeaturesDto(
    boolean petFriendly,
    List<String> otherFacilities
) {
    public HotelFeaturesDto {
        otherFacilities = otherFacilities == null ? List.of() : List.copyOf(otherFacilities);
    }
}
