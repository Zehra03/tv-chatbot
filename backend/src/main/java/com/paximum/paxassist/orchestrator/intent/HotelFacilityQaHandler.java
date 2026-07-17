package com.paximum.paxassist.orchestrator.intent;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
import com.paximum.paxassist.orchestrator.OrchestrationContext;
import com.paximum.paxassist.orchestrator.OrchestrationResult;
import com.paximum.paxassist.validator.ValidationOrchestrator;
import com.paximum.paxassist.validator.ValidationOutcome;
import com.paximum.paxassist.validator.ValidationResult;

/**
 * Handles {@link IntentType#HOTEL_FACILITY_QA}: a facility/board/theme question about ONE specific
 * hotel the user references ("ilk otelde neler var", "TEST KARS2'de havuz var mı", "bu otelde evcil
 * hayvan kabul ediliyor mu"). Instead of the old canned "teyit edemiyorum" reply, it resolves which
 * hotel is meant from the last shown results, fetches the REAL feature model from
 * {@link HotelDetailsService} ({@code GET /hotels/{id}/details}) and lets the model answer strictly
 * from that data (see the {@code [OTEL_OZELLIK_VERISI]} contract in {@code PaxiSystemPrompt}).
 *
 * <p>The chatbot still never fabricates: when the hotel can't be resolved it ASKS which one, and the
 * answer is grounded only in the fetched data (sea view etc. remain unanswerable).
 */
@Component
public class HotelFacilityQaHandler implements IntentHandler {

    private static final Logger log = LoggerFactory.getLogger(HotelFacilityQaHandler.class);

    private static final Locale TR = Locale.of("tr", "TR");
    private static final Pattern FIRST_NUMBER = Pattern.compile("\\d+");
    private static final Map<String, Integer> ORDINAL_WORDS = Map.of(
            "birinci", 1, "ikinci", 2, "üçüncü", 3, "dördüncü", 4, "beşinci", 5);

    private static final String NO_RESULTS =
            "Hangi otel hakkında sorduğunu anlamam için önce bir otel araması yapmamız lazım. Ne aramak istersin?";
    private static final String NOT_HOTEL_RESULTS =
            "Bu özellik bilgisi yalnızca oteller için var. İstersen bir otel araması yapabiliriz.";
    private static final String SAFE_FALLBACK =
            "Bu otel için özellik bilgisini şu an getiremedim. İstersen tekrar dene ya da başka bir otel sor.";
    private static final String BULK_NOT_SUPPORTED =
            "Bunu her otel için tek tek kontrol edebilirim. Hangisinden başlayalım? Numarasını (\"1\") ya da adını söyleyebilirsin.";

    // Plural/collective answers ("hepsi", "tümü") the per-hotel lookup can't satisfy in one shot; caught
    // so they get a graceful "tek tek soralım" reply instead of silently looping the same question.
    private static final List<String> BULK_WORDS = List.of("hepsi", "hepsin", "tümü", "tümün", "tamamı");

    private final HotelDetailsService hotelDetailsService;
    private final ChatService chatService;
    private final ValidationOrchestrator validationOrchestrator;
    private final GreetingNameService greetingNameService;

    public HotelFacilityQaHandler(HotelDetailsService hotelDetailsService,
                                  ChatService chatService,
                                  ValidationOrchestrator validationOrchestrator,
                                  GreetingNameService greetingNameService) {
        this.hotelDetailsService = hotelDetailsService;
        this.chatService = chatService;
        this.validationOrchestrator = validationOrchestrator;
        this.greetingNameService = greetingNameService;
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
        boolean anyHotel = cards.stream().anyMatch(HotelProduct.class::isInstance);
        if (!anyHotel) {
            session.setPendingFacilityQuestion(null);
            return OrchestrationResult.message(NOT_HOTEL_RESULTS);
        }

        // The question to answer: the one we were already waiting on (asked before "hangi otel?"),
        // otherwise this turn's message. This is what survives the "1"-style resume answer.
        String pending = session.getPendingFacilityQuestion();
        String question = (pending != null && !pending.isBlank()) ? pending : context.userMessage();

        // The hotel reference: the AI's selectionReference, else the raw message (a resume like "1").
        String reference = context.criteria() != null ? context.criteria().selectionReference() : null;
        if (reference == null || reference.isBlank()) {
            reference = context.userMessage();
        }

        HotelProduct hotel = resolveHotel(cards, reference);
        if (hotel == null) {
            // Couldn't pin a single hotel. Remember the question so the NEXT answer resumes here,
            // and either handle a bulk ("hepsi") ask gracefully or ask which hotel — never loop silently.
            session.setPendingFacilityQuestion(question);
            return OrchestrationResult.message(
                    isBulkReference(context.userMessage()) ? BULK_NOT_SUPPORTED : askWhichHotel(cards));
        }

        // Resolved → the clarification (if any) is now answered.
        session.setPendingFacilityQuestion(null);
        HotelFeatureDetails details;
        try {
            details = hotelDetailsService.getFeatureDetails(hotel.id(), hotel.provider(), hotel.boardType());
        } catch (RuntimeException e) {
            log.warn("Facility detail lookup failed for hotel {} (provider {}): {}",
                    hotel.id(), hotel.provider(), e.getMessage());
            return OrchestrationResult.message(SAFE_FALLBACK);
        }

        String grounding = buildDataBlock(hotel, details);
        String firstName = greetingNameService.firstNameOf(session.getUserId()).orElse(null);
        String reply = answerFromData(context, question, hotel.hotelName(), grounding, firstName);
        return OrchestrationResult.message(reply);
    }

    /**
     * Generates a grounded answer to {@code question} and runs it past the validator (grounding = the
     * real feature data), mirroring {@link FallbackHandler}. {@code question} is the ORIGINAL facility
     * question (which may have been asked a turn earlier, before "hangi otel?"), not the bare "1"-style
     * resume answer. Fails open if the validator is unavailable; on a definitive rejection returns the
     * safe fallback rather than an ungrounded reply.
     */
    private String answerFromData(OrchestrationContext context, String question, String hotelName,
                                  String grounding, String firstName) {
        StringBuilder feedback = new StringBuilder();
        for (int attempt = 1; ; attempt++) {
            // Name the resolved hotel explicitly: the question may carry a relative reference
            // ("en pahalı olanında…") that the SYSTEM already resolved to this hotel, so the model
            // must answer for it and NOT try to re-rank by price (the data block has no price).
            String prompt = grounding
                    + "\n\nKullanıcının sorusu \"" + hotelName + "\" oteli hakkındadır"
                    + " (\"en pahalı\", \"en ucuz\", \"ilk\" gibi ifadeler sistemce zaten bu otele çözüldü;"
                    + " fiyat/sıralama hesaplama, sadece bu otelin özelliğini yanıtla): " + question
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
     * ("ilk", "birinci", "2", "son", "en ucuz") or by hotel name ("TEST KARS2"). Returns null when
     * the reference is missing or matches nothing, so the caller can ask instead of guessing.
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
        return byName(cards, ref);
    }

    private static HotelProduct hotelAt(List<Object> cards, int index) {
        if (index < 0 || index >= cards.size()) {
            return null;
        }
        return (cards.get(index) instanceof HotelProduct hotel) ? hotel : null;
    }

    private static HotelProduct byName(List<Object> cards, String refLower) {
        for (Object card : cards) {
            if (card instanceof HotelProduct hotel && hotel.hotelName() != null) {
                String name = hotel.hotelName().toLowerCase(TR);
                if (name.contains(refLower) || refLower.contains(name)) {
                    return hotel;
                }
            }
        }
        return null;
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

    /** Above this many hotels, listing them all is unusable — guide the user to a name or a filter instead. */
    private static final int MAX_LISTED_HOTELS = 10;

    /**
     * Asks which hotel the user means. For a short list it enumerates them; once the list is large
     * ({@value #MAX_LISTED_HOTELS}+), dumping every name is unusable, so it nudges toward a name or a
     * narrowing filter rather than a giant numbered list.
     */
    private static String askWhichHotel(List<Object> cards) {
        long hotelCount = cards.stream().filter(HotelProduct.class::isInstance).count();
        if (hotelCount > MAX_LISTED_HOTELS) {
            return "Listede " + hotelCount + " otel var — hepsini tek tek yazmak yerine, hangisini"
                    + " kastettiğini otel adıyla söyler misin? İstersen önce filtreleyelim"
                    + " (örn. \"en ucuz 5 otel\" ya da fiyata/yıldıza göre).";
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
