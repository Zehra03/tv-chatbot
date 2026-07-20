package com.paximum.paxassist.hotel;

import java.math.BigDecimal;
import java.util.List;

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
    String image,
    // Real, human-readable hotel feature names taken from TourVisio's pricesearch response
    // (hotel.facilities[].name ∪ hotel.themes[].name), e.g. "Beach Hotel", "Private Beach",
    // "Deniz Kenarında", "Outdoor Pool". Drives the chat feature filter (denize sıfır / havuz / spa …)
    // via orchestrator.intent.HotelFeature. Empty when the provider returned no facility/theme data
    // for the hotel — never guessed.
    List<String> features,
    // The explicit offer token required by the reservation (BeginTransaction) API.
    // Omitted in old constructors and mocks.
    String offerId,
    // TourVisio "owner provider" of this hotel (pricesearch hotel.provider, e.g. 1). Required as
    // ownerProvider by GetProductInfo, so the detail screen (GET /hotels/{id}/details) must send it
    // back — it is surfaced here so the search card carries it. Null when the provider omitted it
    // or for mock/demo cards; never fabricated.
    Integer provider
) {
    /** Null-safe features so filters can iterate without guarding (missing provider data → empty). */
    public HotelProduct {
        if (features == null) {
            features = List.of();
        }
    }

    /**
     * Backwards-compatible constructor for call sites that carry an offerId but no provider
     * (mocks/demo data and pre-provider call sites).
     */
    public HotelProduct(String id, String hotelName, String region, int stars,
                        BigDecimal price, String currency, String boardType, boolean availability,
                        String image, List<String> features, String offerId) {
        this(id, hotelName, region, stars, price, currency, boardType, availability, image, features, offerId, null);
    }

    /**
     * Backwards-compatible constructor for call sites that carry features but no offerId.
     */
    public HotelProduct(String id, String hotelName, String region, int stars,
                        BigDecimal price, String currency, String boardType, boolean availability,
                        String image, List<String> features) {
        this(id, hotelName, region, stars, price, currency, boardType, availability, image, features, null, null);
    }

    /**
     * Backwards-compatible constructor for call sites that carry an image but no features.
     */
    public HotelProduct(String id, String hotelName, String region, int stars,
                        BigDecimal price, String currency, String boardType, boolean availability,
                        String image) {
        this(id, hotelName, region, stars, price, currency, boardType, availability, image, List.of(), null);
    }

    /**
     * Backwards-compatible constructor for call sites that carry no image (mock/demo data and
     * tests). Delegates to the canonical constructor with a null image and no features.
     */
    public HotelProduct(String id, String hotelName, String region, int stars,
                        BigDecimal price, String currency, String boardType, boolean availability) {
        this(id, hotelName, region, stars, price, currency, boardType, availability, null, List.of(), null);
    }
}
