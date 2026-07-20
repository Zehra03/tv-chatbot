package com.paximum.paxassist.hotel.facility;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.paximum.paxassist.hotel.dto.HotelFeatureDetails;
import com.paximum.paxassist.hotel.dto.HotelFeaturesDto;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Builds the frontend-facing {@link HotelFeatureDetails} from a raw TourVisio detail payload
 * ({@code GetProductInfo} / {@code GetOfferDetails}). Runs on the detail screen only, where the full
 * facility/board/theme data is available — the search/listing screen does NOT call this (avoids the
 * per-hotel N+1 that enriching every search result would cause).
 *
 * <p>The payload shapes differ between endpoints (hotel-level
 * {@code hotel.seasons[].facilityCategories[].facilities[]} vs room-level
 * {@code rooms[].facilities[]} / {@code roomInfos[].facilities[]}), and some facilities carry only a
 * name with no id. Rather than hard-code one path, this walks the tree and gathers every
 * {@code facilities[]} entry, every {@code themes[]} name and every {@code boardName} it finds — so it
 * tolerates both endpoints and missing fields. Missing sections yield empty results, never errors.
 */
@Component
public class HotelFeatureMapper {

    private final FacilityMappingService facilityMapping;
    private final ObjectMapper objectMapper;

    public HotelFeatureMapper(FacilityMappingService facilityMapping, ObjectMapper objectMapper) {
        this.facilityMapping = facilityMapping;
        this.objectMapper = objectMapper;
    }

    /** Convenience overload for a raw (untyped) TourVisio response object. */
    public HotelFeatureDetails map(Object rawDetailResponse) {
        return map(objectMapper.valueToTree(rawDetailResponse));
    }

    /**
     * Maps a parsed TourVisio detail payload into the standard feature model. Never returns null and
     * never throws on missing/empty sections (empty facilities → no groups; empty themes → empty
     * {@code themeFilters}; no boards → empty {@code boardOptions}).
     */
    public HotelFeatureDetails map(JsonNode root) {
        Collected collected = new Collected();
        if (root != null && !root.isMissingNode()) {
            collect(root, collected);
        }

        List<String> groups = facilityMapping.groupsFor(collected.facilityIds, collected.facilityNames);
        boolean petFriendly = groups.contains(FacilityMappingService.PET_FRIENDLY);
        List<String> otherFacilities = groups.stream()
                .filter(g -> !g.equals(FacilityMappingService.PET_FRIENDLY))
                .toList();

        HotelFeaturesDto hotelFeatures = new HotelFeaturesDto(petFriendly, otherFacilities);
        List<String> boardOptions = BoardNormalizer.normalizeAll(collected.boardNames);
        List<String> themeFilters = normalizeThemes(collected.themeNames);

        return new HotelFeatureDetails(hotelFeatures, boardOptions, themeFilters);
    }

    /** Accumulates the raw facility ids/names, theme names and board names found while walking the tree. */
    private static final class Collected {
        final List<Integer> facilityIds = new ArrayList<>();
        final List<String> facilityNames = new ArrayList<>();
        final List<String> themeNames = new ArrayList<>();
        final List<String> boardNames = new ArrayList<>();
    }

    private static void collect(JsonNode node, Collected out) {
        if (node.isObject()) {
            java.util.Iterator<String> names = node.fieldNames();
            while (names.hasNext()) {
                String field = names.next();
                JsonNode value = node.path(field);
                switch (field) {
                    case "facilities" -> readFacilities(value, out);
                    case "themes" -> readThemes(value, out);
                    case "boardName" -> {
                        if (value.isTextual() && !value.asText().isBlank()) {
                            out.boardNames.add(value.asText());
                        }
                    }
                    default -> { /* fall through to recursion below */ }
                }
                collect(value, out);
            }
        } else if (node.isArray()) {
            for (JsonNode child : node) {
                collect(child, out);
            }
        }
    }

    private static void readFacilities(JsonNode facilities, Collected out) {
        if (!facilities.isArray()) {
            return;
        }
        for (JsonNode facility : facilities) {
            JsonNode idNode = facility.path("id");
            if (idNode.canConvertToInt()) {
                out.facilityIds.add(idNode.asInt());
            } else if (idNode.isTextual()) {
                try {
                    out.facilityIds.add(Integer.parseInt(idNode.asText().trim()));
                } catch (NumberFormatException ignored) {
                    // non-numeric facility id — fall back to name matching only
                }
            }
            String name = facility.path("name").asText("").trim();
            if (!name.isEmpty()) {
                out.facilityNames.add(name);
            }
        }
    }

    private static void readThemes(JsonNode themes, Collected out) {
        if (!themes.isArray()) {
            return; // empty/absent themes are expected for some hotels — not an error
        }
        for (JsonNode theme : themes) {
            String name = theme.path("name").asText("").trim();
            if (!name.isEmpty()) {
                out.themeNames.add(name);
            }
        }
    }

    /**
     * Uppercases theme names into stable filter keys (e.g. "All Inclusive" → "ALL_INCLUSIVE",
     * "Spa &amp; Relax" → "SPA_RELAX"): any run of non-alphanumeric characters collapses to a single
     * underscore, with leading/trailing underscores trimmed. Empty-safe, distinct, order-preserving.
     * The values are the provider's own theme names — nothing is invented.
     */
    private static List<String> normalizeThemes(List<String> themeNames) {
        Set<String> keys = new LinkedHashSet<>();
        for (String name : themeNames) {
            String key = name.trim().toUpperCase(Locale.ROOT)
                    .replaceAll("[^A-Z0-9]+", "_")
                    .replaceAll("^_+|_+$", "");
            if (!key.isEmpty()) {
                keys.add(key);
            }
        }
        return new ArrayList<>(keys);
    }
}
