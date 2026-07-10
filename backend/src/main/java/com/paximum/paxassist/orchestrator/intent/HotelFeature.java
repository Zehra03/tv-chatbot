package com.paximum.paxassist.orchestrator.intent;

import java.util.List;
import java.util.Locale;

/**
 * The controlled set of hotel "features" the assistant can filter results by. Each constant maps a
 * stable key — what the AI layer extracts into {@code SlotCriteria.features} — to:
 * <ol>
 *   <li>a Turkish {@link #label()} for honest, human replies ("denize sıfır", "havuzlu"), and</li>
 *   <li>keyword fragments matched against a hotel's REAL TourVisio facility + theme names
 *       (carried on {@link com.paximum.paxassist.hotel.HotelProduct#features()}).</li>
 * </ol>
 *
 * <p>Matching is substring/keyword based because the provider's facility names are free text
 * ("Beach Hotel", "100m to Sea", "Private Beach", "Deniz Kenarında", "Outdoor Pool").
 *
 * <p><b>Post-search only.</b> Features are NOT sent to TourVisio (provider theme data is sparse —
 * most hotels carry it only under {@code facilities}); we filter the real result cards instead, so
 * nothing is fabricated: a hotel is kept only when its provider data confirms the feature.
 */
enum HotelFeature {

    SEAFRONT("denize sıfır", "beach", "sea", "deniz", "sahil", "plaj", "mavi bayrak"),
    POOL("havuzlu", "pool", "havuz"),
    AQUAPARK("kaydıraklı", "water slide", "waterslide", "aquapark", "aqua park", "kaydırak", "su parkı"),
    SPA("spa & wellness", "spa", "sauna", "hamam", "turkish bath", "massage", "masaj", "jakuz", "wellness", "kaplıca"),
    KIDS_CLUB("çocuk kulüplü", "kids club", "kids", "çocuk", "cocuk", "mini club"),
    FITNESS("fitness", "fitness", "gym", "spor salonu", "healthy equipment", "sport equipment"),
    PETS_ALLOWED("evcil hayvan kabul eden", "pets allowed", "evcil", "pet friendly");

    private final String label;
    private final List<String> keywords;

    HotelFeature(String label, String... keywords) {
        this.label = label;
        this.keywords = List.of(keywords);
    }

    /** Human-facing Turkish name, used to word the assistant's reply honestly. */
    String label() {
        return label;
    }

    /** @return true if any of this feature's keywords occurs in the (already lower-cased) blob. */
    boolean matches(String featureBlobLower) {
        for (String keyword : keywords) {
            if (featureBlobLower.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    /** Resolve an extracted key ("SEAFRONT", "seafront") to a feature, or null if unrecognised. */
    static HotelFeature fromKey(String key) {
        if (key == null || key.isBlank()) {
            return null;
        }
        try {
            return HotelFeature.valueOf(key.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException unknown) {
            return null;
        }
    }
}
