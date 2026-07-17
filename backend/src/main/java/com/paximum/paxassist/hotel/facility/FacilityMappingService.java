package com.paximum.paxassist.hotel.facility;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Loads {@code facility-mapping.json} (the single source of truth) once at startup and resolves real
 * TourVisio facility ids/names into stable feature-group labels ({@code pool}, {@code spa_wellness},
 * {@code pet_friendly}, …). Used by the hotel layer to build the detail-screen feature model.
 *
 * <p>The AI Intention module maps user keywords to the SAME group labels; keeping both sides pointed
 * at this one file prevents two divergent mappings (see the card's "tek kaynak, tek dosya").
 *
 * <p><b>Unknown ids/names are simply ungrouped</b> — the sample dataset this file was seeded from is
 * incomplete, so an unrecognized facility must never raise an error, only fail to contribute a group.
 */
@Service
public class FacilityMappingService {

    private static final Logger log = LoggerFactory.getLogger(FacilityMappingService.class);

    /** Classpath location of the mapping. A project-appropriate spot per the card (kept on the classpath so it loads at runtime). */
    private static final String MAPPING_RESOURCE = "facility-mapping.json";

    /** Special group surfaced as its own boolean on the response model rather than in the facility list. */
    public static final String PET_FRIENDLY = "pet_friendly";

    /** Group label -> facility ids in that group. */
    private final Map<String, Set<Integer>> idsByGroup = new LinkedHashMap<>();
    /** Group label -> lower-cased facility names in that group. */
    private final Map<String, Set<String>> namesByGroup = new LinkedHashMap<>();
    /** Group label -> lower-cased user keywords for that group (available for AI-Intention-style lookups). */
    private final Map<String, Set<String>> keywordsByGroup = new LinkedHashMap<>();

    public FacilityMappingService(ObjectMapper objectMapper) {
        load(objectMapper);
    }

    private void load(ObjectMapper objectMapper) {
        ClassPathResource resource = new ClassPathResource(MAPPING_RESOURCE);
        try (InputStream in = resource.getInputStream()) {
            JsonNode root = objectMapper.readTree(in);
            java.util.Iterator<String> groups = root.fieldNames();
            while (groups.hasNext()) {
                String group = groups.next();
                // Keys beginning with '_' (e.g. "_meta") are documentation, not groups.
                if (group.startsWith("_")) {
                    continue;
                }
                JsonNode node = root.path(group);
                idsByGroup.put(group, readInts(node.path("facilityIds")));
                namesByGroup.put(group, readLowerStrings(node.path("facilityNames")));
                keywordsByGroup.put(group, readLowerStrings(node.path("keywords")));
            }
            log.info("Loaded facility mapping with {} groups: {}", idsByGroup.size(), idsByGroup.keySet());
        } catch (IOException e) {
            // A missing/broken mapping must not crash the app: consumers just get no groups.
            log.error("Could not load {} — facility grouping disabled: {}", MAPPING_RESOURCE, e.getMessage());
        }
    }

    /**
     * Resolves the distinct feature-group labels covered by the given facility ids and/or names.
     * Matching is by id first, then by case-insensitive name (room-level facilities often carry a
     * name but no id). Group order follows the mapping file for deterministic output. Unknown
     * ids/names contribute nothing.
     */
    public List<String> groupsFor(Collection<Integer> facilityIds, Collection<String> facilityNames) {
        Set<Integer> ids = facilityIds == null ? Set.of() : new LinkedHashSet<>(facilityIds);
        Set<String> names = new LinkedHashSet<>();
        if (facilityNames != null) {
            for (String name : facilityNames) {
                if (name != null && !name.isBlank()) {
                    names.add(name.trim().toLowerCase(Locale.ROOT));
                }
            }
        }
        List<String> matched = new ArrayList<>();
        for (String group : idsByGroup.keySet()) {
            if (intersects(idsByGroup.get(group), ids) || intersects(namesByGroup.get(group), names)) {
                matched.add(group);
            }
        }
        return matched;
    }

    /**
     * Resolves the feature-group labels a free-text phrase mentions, by scanning each group's
     * keywords ({@code "havuzlu"} → {@code pool}). Offered so a keyword→label lookup can be shared
     * from this one file rather than duplicated; the AI Intention module currently uses its own
     * embedded key list and does not yet call this. Case-insensitive substring match; group order is
     * deterministic. Never throws — an unrecognized phrase yields an empty list.
     */
    public List<String> groupsForText(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        String haystack = text.toLowerCase(Locale.ROOT);
        List<String> matched = new ArrayList<>();
        for (Map.Entry<String, Set<String>> entry : keywordsByGroup.entrySet()) {
            for (String keyword : entry.getValue()) {
                if (haystack.contains(keyword)) {
                    matched.add(entry.getKey());
                    break;
                }
            }
        }
        return matched;
    }

    private static <T> boolean intersects(Set<T> a, Set<T> b) {
        if (a == null || a.isEmpty() || b.isEmpty()) {
            return false;
        }
        // Iterate the smaller set for a cheap intersection test.
        Set<T> small = a.size() <= b.size() ? a : b;
        Set<T> large = small == a ? b : a;
        for (T item : small) {
            if (large.contains(item)) {
                return true;
            }
        }
        return false;
    }

    private static Set<Integer> readInts(JsonNode array) {
        Set<Integer> result = new LinkedHashSet<>();
        if (array != null && array.isArray()) {
            for (JsonNode node : array) {
                if (node.canConvertToInt()) {
                    result.add(node.asInt());
                } else if (node.isTextual()) {
                    try {
                        result.add(Integer.parseInt(node.asText().trim()));
                    } catch (NumberFormatException ignored) {
                        // non-numeric id in the mapping file — skip it
                    }
                }
            }
        }
        return result;
    }

    private static Set<String> readLowerStrings(JsonNode array) {
        Set<String> result = new LinkedHashSet<>();
        if (array != null && array.isArray()) {
            for (JsonNode node : array) {
                String value = node.asText("").trim();
                if (!value.isEmpty()) {
                    result.add(value.toLowerCase(Locale.ROOT));
                }
            }
        }
        return result;
    }
}
