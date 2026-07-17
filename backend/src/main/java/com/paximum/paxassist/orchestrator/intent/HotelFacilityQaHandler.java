package com.paximum.paxassist.orchestrator.intent;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.paximum.paxassist.ai.IntentType;
import com.paximum.paxassist.auth.service.GreetingNameService;
import com.paximum.paxassist.chat.domain.ChatSession;
import com.paximum.paxassist.chat.service.ChatService;
import com.paximum.paxassist.hotel.HotelDetailsService;
import com.paximum.paxassist.hotel.HotelProduct;
import com.paximum.paxassist.hotel.dto.HotelFeatureDetails;
import com.paximum.paxassist.hotel.facility.FacilityMappingService;
import com.paximum.paxassist.orchestrator.OrchestrationContext;
import com.paximum.paxassist.orchestrator.OrchestrationResult;
import com.paximum.paxassist.validator.ValidationOrchestrator;
import com.paximum.paxassist.validator.ValidationOutcome;
import com.paximum.paxassist.validator.ValidationResult;

/**
 * Handles {@link IntentType#HOTEL_FACILITY_QA}: a facility/board/theme question about ONE specific
 * hotel the user references ("ilk otelde neler var", "TEST KARS2'de havuz var mı"), or a bulk
 * question over the whole shown list ("hepsinde havuz var mı"). Resolves which hotel(s) are meant
 * from the last shown results, fetches the REAL feature model from {@link HotelDetailsService} and
 * answers strictly from that data (see the {@code [OTEL_OZELLIK_VERISI]} contract in
 * {@code PaxiSystemPrompt}).
 *
 * <p>The chatbot never fabricates: unresolved hotels are asked about (distinguishing "named a hotel
 * not in the list" from "gave no reference"), and answers are grounded only in fetched data.
 */
@Component
public class HotelFacilityQaHandler implements IntentHandler {

    private static final Logger log = LoggerFactory.getLogger(HotelFacilityQaHandler.class);

    private static final Locale TR = Locale.of("tr", "TR");
    private static final Pattern FIRST_NUMBER = Pattern.compile("\\d+");
    private static final Map<String, Integer> ORDINAL_WORDS = Map.of(
            "birinci", 1, "ikinci", 2, "üçüncü", 3, "dördüncü", 4, "beşinci", 5);

    /** Above this many hotels, listing/answering them all is unusable — nudge toward a name or filter. */
    private static final int MAX_LISTED_HOTELS = 10;

    private static final String NO_RESULTS =
            "Hangi otel hakkında sorduğunu anlamam için önce bir otel araması yapmamız lazım. Ne aramak istersin?";
    private static final String NOT_HOTEL_RESULTS =
            "Bu özellik bilgisi yalnızca oteller için var. İstersen bir otel araması yapabiliriz.";
    private static final String SAFE_FALLBACK =
            "Bu otel için özellik bilgisini şu an getiremedim. İstersen tekrar dene ya da başka bir otel sor.";

    /** Collective references ("hepsi", "tümü", "hepsinde") — trigger the per-hotel bulk answer. */
    private static final List<String> BULK_WORDS = List.of("hepsi", "hepsin", "tümü", "tümün", "tamamı");
    /** Turkish question particles — a message carrying one is a fresh question, not a bare reference. */
    private static final Set<String> QUESTION_PARTICLES = Set.of("mı", "mi", "mu", "mü");

    /** Group key -> Turkish label for the bulk answer lines. */
    private static final Map<String, String> GROUP_TR = Map.ofEntries(
            Map.entry("pool", "Havuz"),
            Map.entry("aquapark", "Su kaydırağı"),
            Map.entry("kids", "Çocuk kulübü"),
            Map.entry("pet_friendly", "Evcil hayvan kabulü"),
            Map.entry("spa_wellness", "Spa/wellness"),
            Map.entry("sports", "Spor"),
            Map.entry("beach", "Plaj"),
            Map.entry("business", "İş/toplantı"),
            Map.entry("dining_bar", "Restoran/bar"));

    private final HotelDetailsService hotelDetailsService;
    private final ChatService chatService;
    private final ValidationOrchestrator validationOrchestrator;
    private final GreetingNameService greetingNameService;
    private final FacilityMappingService facilityMapping;

    public HotelFacilityQaHandler(HotelDetailsService hotelDetailsService,
                                  ChatService chatService,
                                  ValidationOrchestrator validationOrchestrator,
                                  GreetingNameService greetingNameService,
                                  FacilityMappingService facilityMapping) {
        this.hotelDetailsService = hotelDetailsService;
        this.chatService = chatService;
        this.validationOrchestrator = validationOrchestrator;
        this.greetingNameService = greetingNameService;
        this.facilityMapping = facilityMapping;
    }

    @Override
    public boolean supports(IntentType intent) {
        return intent == IntentType.HOTEL_FACILITY_QA;
    }

    @Override
    public OrchestrationResult handle(OrchestrationContext context) {
        ChatSession session = context.session();
        List<Object> cards = session.getLastResultCards();
        if (cards == null || cards.isEmpty()) {
            session.setPendingFacilityQuestion(null);
            return OrchestrationResult.message(NO_RESULTS);
        }
        if (cards.stream().noneMatch(HotelProduct.class::isInstance)) {
            session.setPendingFacilityQuestion(null);
            return OrchestrationResult.message(NOT_HOTEL_RESULTS);
        }

        String userMessage = context.userMessage();
        String pending = session.getPendingFacilityQuestion();
        boolean fresh = isFreshFacilityQuestion(userMessage);

        // Which facility question to answer:
        //  - fresh question (has "mı/neler" or a facility word) -> the CURRENT message (replaces stale
        //    pending), so "havuz var mı" is never answered with an earlier "evcil hayvan" question;
        //  - bare reference ("1"/"ilk"/"en pahalı"/isim) OR meaningless answer ("evet"/"tamam") -> the
        //    PENDING question, if one is waiting.
        String question = fresh ? userMessage
                : (pending != null && !pending.isBlank() ? pending : userMessage);

        // Bulk ("hepsinde ... var mı"): answer every shown hotel (<= MAX) or nudge to filter.
        if (isBulkReference(userMessage)) {
            return handleBulk(session, cards, question);
        }

        String selRef = context.criteria() != null ? context.criteria().selectionReference() : null;
        boolean explicitRef = selRef != null && !selRef.isBlank();
        HotelProduct hotel = resolveHotel(cards, explicitRef ? selRef : userMessage);

        if (hotel == null) {
            if (explicitRef) {
                // The user named a hotel that isn't in the SHOWN (filtered) list. If it exists in the raw
                // results, just answer about it — asking "filtreyi kaldırayım mı?" would only add a step,
                // and actually dropping the filter would desync the UI's card panel. The user's filter is
                // deliberately left untouched so later bulk questions still apply to it.
                HotelProduct outside = byName(session.getLastApiResultCards(), selRef);
                if (outside != null) {
                    session.setPendingFacilityQuestion(null);
                    return OrchestrationResult.message("\"" + outside.hotelName() + "\" filtrelenmiş listede"
                            + " yok, ama otelin bilgisine bakıyorum: "
                            + answerSingle(context, outside, question).reply());
                }
            }
            // A fresh question that still needs a hotel becomes the new pending. A bare/meaningless answer
            // ("evet") must NOT overwrite or clear an existing pending — keep waiting on the same question.
            if (fresh) {
                session.setPendingFacilityQuestion(userMessage);
            }
            if (selRef != null && !selRef.isBlank()) {
                return OrchestrationResult.message(nameNotFoundMessage(selRef.trim()));
            }
            return OrchestrationResult.message(askWhichHotel(cards));
        }

        // Resolved → clarification (if any) is answered.
        session.setPendingFacilityQuestion(null);
        return answerSingle(context, hotel, question);
    }

    /**
     * Fetches the real feature data for one hotel and answers {@code question}. A SPECIFIC facility
     * question ("havuz var mı", "evcil hayvan", "spa") is answered DETERMINISTICALLY from the data —
     * no LLM — which both avoids the model derailing on a relative reference it must not re-compute
     * ("en pahalı olanında…") and needs no model/validator round-trip. Only an open question
     * ("neler var") or an unmapped concept (sea view) falls through to the grounded LLM, and there we
     * strip the relative/positional reference first.
     */
    private OrchestrationResult answerSingle(OrchestrationContext context, HotelProduct hotel, String question) {
        HotelFeatureDetails details;
        try {
            details = hotelDetailsService.getFeatureDetails(hotel.id(), hotel.provider(), hotel.boardType());
        } catch (RuntimeException e) {
            log.warn("Facility detail lookup failed for hotel {} (provider {}): {}",
                    hotel.id(), hotel.provider(), e.getMessage());
            return OrchestrationResult.message(SAFE_FALLBACK);
        }

        List<String> groups = facilityMapping.groupsForText(question);
        if (!groups.isEmpty()) {
            return OrchestrationResult.message(answerFacilityGroups(hotel, details, groups));
        }

        String cleaned = stripReference(question);
        String grounding = buildDataBlock(hotel, details);
        String firstName = greetingNameService.firstNameOf(context.session().getUserId()).orElse(null);
        return OrchestrationResult.message(answerFromData(context, cleaned, hotel.hotelName(), grounding, firstName));
    }

    /** Deterministic, LLM-free answer for a hotel's specific facility group(s), grounded in real data. */
    private static String answerFacilityGroups(HotelProduct hotel, HotelFeatureDetails details, List<String> groups) {
        if (groups.size() == 1) {
            String group = groups.get(0);
            String label = GROUP_TR.getOrDefault(group, group).toLowerCase(TR);
            return groupPresent(group, details)
                    ? "Evet, " + hotel.hotelName() + " otelinde " + label + " var."
                    : hotel.hotelName() + " için " + label + " bilgisi elimde görünmüyor"
                            + " (bu, kesin olarak olmadığı anlamına gelmez).";
        }
        StringBuilder sb = new StringBuilder(hotel.hotelName() + " otelinde:");
        for (String group : groups) {
            sb.append("\n- ").append(GROUP_TR.getOrDefault(group, group)).append(": ")
              .append(groupPresent(group, details) ? "var" : "görünmüyor");
        }
        return sb.toString();
    }

    /** Relative/positional references the SYSTEM already resolved — stripped before the LLM sees the question. */
    private static final Pattern REFERENCE_PHRASES = Pattern.compile(
            "(en pahalı|en pahali|en ucuz)( olanı(nda|nın)?| otelde| otelin)?"
                    + "|(ilk|son|sonuncu|birinci|ikinci|üçüncü|dördüncü|beşinci)( otelde| otel)?"
                    + "|\\d+\\s*\\.?\\s*otelde"
                    + "|(bu|şu|o) otelde",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    private static String stripReference(String question) {
        return REFERENCE_PHRASES.matcher(question).replaceAll(" ").trim().replaceAll("\\s+", " ");
    }

    /**
     * Answers a "hepsinde …" question over the shown hotels. For a small list (&le; {@value
     * #MAX_LISTED_HOTELS}) it checks each hotel deterministically for the asked facility group(s) and
     * reports one line per hotel; a larger list is nudged to filter first. Terminal — clears pending.
     */
    private OrchestrationResult handleBulk(ChatSession session, List<Object> cards, String question) {
        session.setPendingFacilityQuestion(null);
        List<HotelProduct> hotels = cards.stream()
                .filter(HotelProduct.class::isInstance).map(HotelProduct.class::cast).toList();
        if (hotels.size() > MAX_LISTED_HOTELS) {
            return OrchestrationResult.message("Şu an listede " + hotels.size() + " otel var — hepsini tek tek"
                    + " kontrol etmek çok uzun olur. Önce filtreleyelim mi (örn. \"en ucuz 5 otel\" ya da"
                    + " yıldıza/fiyata göre), sonra hepsine tek tek bakarım.");
        }
        List<String> groups = facilityMapping.groupsForText(question);
        if (groups.isEmpty()) {
            return OrchestrationResult.message("Her otel için neyi kontrol edeyim? Örneğin \"hepsinde havuz"
                    + " var mı\" ya da \"hepsinde spa var mı\" diyebilirsin.");
        }

        StringBuilder sb = new StringBuilder(describeGroups(groups)).append(" durumu:");
        for (HotelProduct hotel : hotels) {
            HotelFeatureDetails details;
            try {
                details = hotelDetailsService.getFeatureDetails(hotel.id(), hotel.provider(), hotel.boardType());
            } catch (RuntimeException e) {
                log.warn("Bulk facility lookup failed for hotel {} (provider {}): {}",
                        hotel.id(), hotel.provider(), e.getMessage());
                sb.append("\n- ").append(hotel.hotelName()).append(": bilgiyi alamadım");
                continue;
            }
            boolean all = groups.stream().allMatch(g -> groupPresent(g, details));
            boolean none = groups.stream().noneMatch(g -> groupPresent(g, details));
            // "görünmüyor" (not "yok"): facility verisi sınırlı olabilir, kesin negatif iddia etmiyoruz.
            sb.append("\n- ").append(hotel.hotelName()).append(": ")
              .append(all ? "var" : none ? "görünmüyor" : "kısmen var");
        }
        return OrchestrationResult.message(sb.toString());
    }

    /**
     * True when a facility group is present for a hotel. Crucially checks BOTH the grouped facilities
     * ({@code otherFacilities}) AND the separately-modelled {@code petFriendly} flag, so a pet question
     * is answered from the boolean rather than silently missed by only scanning {@code otherFacilities}.
     */
    private static boolean groupPresent(String group, HotelFeatureDetails details) {
        if (FacilityMappingService.PET_FRIENDLY.equals(group)) {
            return details.hotelFeatures().petFriendly();
        }
        return details.hotelFeatures().otherFacilities().contains(group);
    }

    private static String describeGroups(List<String> groups) {
        return groups.stream().map(g -> GROUP_TR.getOrDefault(g, g)).distinct().collect(Collectors.joining(", "));
    }

    /**
     * Distinguishes a fresh facility question (has a question particle {@code mı/mi/mu/mü}, "neler",
     * or a known facility keyword) from a bare reference ("1", "ilk", "en pahalı") or a meaningless
     * answer ("evet", "tamam"). Only a fresh question overrides a pending question.
     */
    private boolean isFreshFacilityQuestion(String message) {
        if (message == null || message.isBlank()) {
            return false;
        }
        String m = message.toLowerCase(TR);
        if (m.contains("neler") || m.contains("nedir")) {
            return true;
        }
        for (String token : m.split("[^\\p{L}]+")) {
            if (QUESTION_PARTICLES.contains(token)) {
                return true;
            }
        }
        return !facilityMapping.groupsForText(m).isEmpty();
    }

    /**
     * Generates a grounded answer to {@code question} and runs it past the validator (grounding = the
     * real feature data), mirroring {@link FallbackHandler}. Fails open if the validator is unavailable;
     * on a definitive rejection returns the safe fallback rather than an ungrounded reply.
     */
    private String answerFromData(OrchestrationContext context, String question, String hotelName,
                                  String grounding, String firstName) {
        StringBuilder feedback = new StringBuilder();
        for (int attempt = 1; ; attempt++) {
            // The hotel is already resolved and named; the reference has been stripped from the question,
            // so the model just answers for THIS hotel from the data block (no price/ranking reasoning).
            String prompt = grounding
                    + "\n\nSoru, yukarıdaki \"" + hotelName + "\" oteli hakkındadır; yalnızca bu otelin"
                    + " verisiyle yanıtla: " + question
                    + (feedback.length() == 0 ? ""
                       : "\n\n[Sistem notu: Önceki yanıtın uygun değildi, düzelt: " + feedback + "]");
            String candidate = chatService.chat(prompt, firstName).reply();

            ValidationOutcome outcome;
            try {
                outcome = validationOrchestrator.validate(context.session().getId(), question, candidate,
                        grounding, attempt);
            } catch (RuntimeException e) {
                log.warn("Validator unavailable on attempt {}, accepting grounded answer as-is: {}",
                        attempt, e.getMessage());
                return candidate;
            }
            if (outcome.result().verdict() == ValidationResult.Verdict.APPROVED) {
                return candidate;
            }
            if (!outcome.retryAllowed()) {
                log.info("Validator rejected the facility answer and no retry remains; safe fallback.");
                return SAFE_FALLBACK;
            }
            String critic = outcome.result().feedback();
            if (critic != null && !critic.isBlank()) {
                feedback.append(critic).append(' ');
            }
        }
    }

    /** The real feature data, tagged so the system prompt answers strictly from it (and validator grounds on it). */
    private static String buildDataBlock(HotelProduct hotel, HotelFeatureDetails details) {
        return "[OTEL_OZELLIK_VERISI]\n"
                + "otel_adi: " + hotel.hotelName() + "\n"
                + "evcil_hayvan (petFriendly): " + details.hotelFeatures().petFriendly() + "\n"
                + "diger_olanaklar (otherFacilities): " + String.join(", ", details.hotelFeatures().otherFacilities()) + "\n"
                + "pansiyon (boardOptions): " + String.join(", ", details.boardOptions()) + "\n"
                + "temalar (themeFilters): " + String.join(", ", details.themeFilters()) + "\n"
                + "[/OTEL_OZELLIK_VERISI]";
    }

    /**
     * Resolves the referenced hotel from the shown cards: by ordinal/positional reference
     * ("ilk", "birinci", "2", "son"), by price ("en ucuz"/"en pahalı" — over the SHOWN list) or by
     * hotel name. Returns null when the reference is missing or matches nothing.
     */
    private HotelProduct resolveHotel(List<Object> cards, String reference) {
        if (reference == null || reference.isBlank()) {
            return null;
        }
        String ref = reference.trim().toLowerCase(TR);

        if (ref.contains("en ucuz")) {
            return byPrice(cards, true);
        }
        if (ref.contains("en pahalı") || ref.contains("en pahali")) {
            return byPrice(cards, false);
        }
        if (ref.startsWith("ilk")) {
            return hotelAt(cards, 0);
        }
        if (ref.startsWith("son")) {
            return hotelAt(cards, cards.size() - 1);
        }
        for (Map.Entry<String, Integer> entry : ORDINAL_WORDS.entrySet()) {
            if (ref.startsWith(entry.getKey())) {
                return hotelAt(cards, entry.getValue() - 1);
            }
        }
        Matcher matcher = FIRST_NUMBER.matcher(ref);
        if (matcher.find()) {
            return hotelAt(cards, Integer.parseInt(matcher.group()) - 1);
        }
        // Fall back to matching the reference against a hotel name ("TEST KARS2'de havuz var mı").
        return byName(cards, reference);
    }

    private static HotelProduct hotelAt(List<Object> cards, int index) {
        if (index < 0 || index >= cards.size()) {
            return null;
        }
        return (cards.get(index) instanceof HotelProduct hotel) ? hotel : null;
    }

    /**
     * Case- and diacritic-insensitive hotel name match. Uses {@link #fold} rather than a Turkish
     * lower-case: {@code toLowerCase(tr)} maps capital {@code I} to dotless {@code ı}, so
     * "MELIHOTEL" would never match the hotel "melihotel". Matching is bidirectional so either a bare
     * name or a whole question ("melihotel kemer'de havuz var mı") resolves.
     */
    private static HotelProduct byName(List<Object> cards, String reference) {
        String needle = fold(reference);
        if (cards == null || needle.isEmpty()) {
            return null;
        }
        for (Object card : cards) {
            if (card instanceof HotelProduct hotel && hotel.hotelName() != null) {
                String name = fold(hotel.hotelName());
                if (!name.isEmpty() && (name.contains(needle) || needle.contains(name))) {
                    return hotel;
                }
            }
        }
        return null;
    }

    /**
     * Folds text for locale-safe comparison: strips diacritics (ü→u, ş→s, ğ→g), lower-cases with the
     * ROOT locale and maps dotless {@code ı} to {@code i}. Mirrors {@code HotelSearchServiceImpl.fold}.
     */
    private static String fold(String value) {
        if (value == null) {
            return "";
        }
        String normalized = Normalizer.normalize(value.trim(), Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
        return normalized.toLowerCase(Locale.ROOT).replace("ı", "i");
    }

    private static HotelProduct byPrice(List<Object> cards, boolean cheapest) {
        HotelProduct best = null;
        for (Object card : cards) {
            if (!(card instanceof HotelProduct hotel) || hotel.price() == null) {
                continue;
            }
            if (best == null
                    || (cheapest ? hotel.price().compareTo(best.price()) < 0
                                 : hotel.price().compareTo(best.price()) > 0)) {
                best = hotel;
            }
        }
        return best;
    }

    /**
     * Message for when the user named a hotel we can't find at all — neither in the shown list nor in the
     * raw results (a hotel that was merely filtered out is answered directly instead, see {@link #handle}).
     */
    private static String nameNotFoundMessage(String name) {
        return "\"" + name + "\" adıyla eşleşen bir otel bulamadım. Listedekilerden birini mi kastettin?"
                + " Numarasını (\"1\") ya da adını yazabilirsin.";
    }

    /**
     * Asks which hotel the user means (no reference given). Short list → enumerate; large list
     * ({@value #MAX_LISTED_HOTELS}+) → nudge toward a name or a narrowing filter.
     */
    private static String askWhichHotel(List<Object> cards) {
        long hotelCount = cards.stream().filter(HotelProduct.class::isInstance).count();
        if (hotelCount > MAX_LISTED_HOTELS) {
            return "Listede " + hotelCount + " otel var. Hangi otel için soruyorsun?"
                    + " Adını yazman yeterli — istersen önce filtreleyebiliriz.";
        }
        StringBuilder sb = new StringBuilder("Hangi otel hakkında soruyorsun? Şu an listede:");
        int i = 1;
        for (Object card : cards) {
            if (card instanceof HotelProduct hotel) {
                sb.append("\n").append(i).append(". ").append(hotel.hotelName());
            }
            i++;
        }
        sb.append("\nNumarasını (\"1\", \"ilk\") ya da adını söyleyebilirsin.");
        return sb.toString();
    }

    /** True when the message asks about ALL hotels at once ("hepsinde havuz var mı", "tümünde spa var mı"). */
    private static boolean isBulkReference(String message) {
        if (message == null) {
            return false;
        }
        String m = message.toLowerCase(TR);
        return BULK_WORDS.stream().anyMatch(m::contains);
    }
}
