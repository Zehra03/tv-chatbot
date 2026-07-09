package com.paximum.paxassist.hotel;

import java.math.BigDecimal;

public record HotelProduct(
    String id,
    String hotelName,
    String region,
    int stars,
    BigDecimal price,
    String currency,
    String boardType,
    boolean availability,
    // Absolute hotel image URL from TourVisio (pricesearch hotel.thumbnailFull); null when the
    // provider has no image for that hotel. Never fabricated — the frontend shows a placeholder.
    String image
) {
    /**
     * Backwards-compatible constructor for call sites that carry no image (mock/demo data and
     * tests). Delegates to the canonical constructor with a null image.
     */
    public HotelProduct(String id, String hotelName, String region, int stars,
                        BigDecimal price, String currency, String boardType, boolean availability) {
        this(id, hotelName, region, stars, price, currency, boardType, availability, null);
    }
}
