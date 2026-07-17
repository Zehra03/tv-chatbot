package com.paximum.paxassist.orchestrator.intent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.paximum.paxassist.ai.IntentType;
import com.paximum.paxassist.ai.SlotCriteria;
import com.paximum.paxassist.auth.service.GreetingNameService;
import com.paximum.paxassist.chat.domain.ChatSession;
import com.paximum.paxassist.chat.dto.AiReply;
import com.paximum.paxassist.chat.service.ChatService;
import com.paximum.paxassist.hotel.HotelDetailsService;
import com.paximum.paxassist.hotel.HotelProduct;
import com.paximum.paxassist.hotel.dto.HotelFeatureDetails;
import com.paximum.paxassist.hotel.dto.HotelFeaturesDto;
import com.paximum.paxassist.hotel.facility.FacilityMappingService;
import com.paximum.paxassist.orchestrator.OrchestrationContext;
import com.paximum.paxassist.orchestrator.OrchestrationResult;
import com.paximum.paxassist.validator.ValidationOrchestrator;

/**
 * Unit tests for {@link HotelFacilityQaHandler}: resolving which hotel is meant (ordinal / name /
 * price / ambiguous), the stale-pending guard, name-not-in-shown-list distinctions, and the bulk
 * ("hepsinde …") per-hotel answer. A REAL {@link FacilityMappingService} (loads the classpath
 * mapping) backs keyword/group detection; the LLM + validator are mocked, with the validator forced
 * to throw so the fail-open path returns the generated answer verbatim.
 */
class HotelFacilityQaHandlerTest {

    private HotelDetailsService detailsService;
    private ChatService chatService;
    private ValidationOrchestrator validator;
    private GreetingNameService greetingNameService;
    private HotelFacilityQaHandler handler;

    private static final HotelProduct HOTEL_A =
            new HotelProduct("A", "Rixos Premium", "Antalya", 5, new BigDecimal("100"), "EUR",
                    "ALL INCLUSIVE", true, null, List.of(), "offer-a", 1);
    private static final HotelProduct HOTEL_B =
            new HotelProduct("B", "TEST KARS2", "Antalya", 5, new BigDecimal("200"), "EUR",
                    "HB", true, null, List.of(), "offer-b", 7);
    /** Only ever in the raw (unfiltered) list — used for the "named but filtered out" case. */
    private static final HotelProduct HOTEL_C =
            new HotelProduct("C", "melihotel kemer", "Antalya", 4, new BigDecimal("300"), "EUR",
                    "BB", true, null, List.of(), "offer-c", 1);

    private static final HotelFeatureDetails EMPTY_DETAILS =
            new HotelFeatureDetails(new HotelFeaturesDto(false, List.of()), List.of(), List.of());

    @BeforeEach
    void setUp() {
        detailsService = mock(HotelDetailsService.class);
        chatService = mock(ChatService.class);
        validator = mock(ValidationOrchestrator.class);
        greetingNameService = mock(GreetingNameService.class);
        handler = new HotelFacilityQaHandler(detailsService, chatService, validator, greetingNameService,
                new FacilityMappingService(new ObjectMapper()));

        when(greetingNameService.firstNameOf(nullable(Long.class))).thenReturn(Optional.empty());
        when(detailsService.getFeatureDetails(anyString(), nullable(Integer.class), nullable(String.class)))
                .thenReturn(EMPTY_DETAILS);
        when(chatService.chat(anyString(), nullable(String.class))).thenReturn(new AiReply("stub answer"));
        // Force the fail-open path so the handler returns the generated answer without a ValidationOutcome.
        when(validator.validate(nullable(String.class), anyString(), anyString(), anyString(), anyInt()))
                .thenThrow(new RuntimeException("validator off in test"));
    }

    private OrchestrationContext context(List<Object> cards, String selectionReference) {
        return context(cards, selectionReference, "soru?");
    }

    private OrchestrationContext context(List<Object> cards, String selectionReference, String message) {
        ChatSession session = new ChatSession("s1");
        session.setLastResultCards(cards);
        return new OrchestrationContext(session, message, IntentType.HOTEL_FACILITY_QA, sel(selectionReference));
    }

    @Test
    void noResultsAsksToSearchFirst() {
        OrchestrationResult r = handler.handle(context(List.of(), "ilk"));
        assertTrue(r.reply().contains("önce bir otel araması"));
        verify(detailsService, never()).getFeatureDetails(anyString(), any(), any());
    }

    @Test
    void ambiguousReferenceAsksWhichHotel() {
        OrchestrationResult r = handler.handle(context(List.of(HOTEL_A, HOTEL_B), null));
        assertTrue(r.reply().contains("Hangi otel"));
        assertTrue(r.reply().contains("Rixos Premium"));
        verify(detailsService, never()).getFeatureDetails(anyString(), any(), any());
    }

    @Test
    void ordinalReferenceResolvesFirstHotelAndCallsDetails() {
        OrchestrationResult r = handler.handle(context(List.of(HOTEL_A, HOTEL_B), "ilk"));
        assertEquals("stub answer", r.reply());
        verify(detailsService).getFeatureDetails("A", 1, "ALL INCLUSIVE");
    }

    @Test
    void numberReferenceResolvesSecondHotel() {
        handler.handle(context(List.of(HOTEL_A, HOTEL_B), "2"));
        verify(detailsService).getFeatureDetails("B", 7, "HB");
    }

    @Test
    void nameReferenceResolvesMatchingHotel() {
        handler.handle(context(List.of(HOTEL_A, HOTEL_B), "TEST KARS2"));
        verify(detailsService).getFeatureDetails("B", 7, "HB");
    }

    @Test
    void priceReferenceResolvesAgainstTheShownList() {
        // "en pahalı" must pick the most expensive of the SHOWN cards (B=200 > A=100), not some raw list.
        handler.handle(context(List.of(HOTEL_A, HOTEL_B), null, "en pahalı olanında havuz var mı"));
        verify(detailsService).getFeatureDetails("B", 7, "HB");
    }

    @Test
    void nonHotelResultsAreRejected() {
        OrchestrationResult r = handler.handle(context(List.of("a flight card"), "ilk"));
        assertTrue(r.reply().contains("yalnızca oteller"));
        verify(detailsService, never()).getFeatureDetails(anyString(), any(), any());
    }

    @Test
    void unresolvedFreshQuestionRemembersItForResume() {
        OrchestrationContext ctx = context(List.of(HOTEL_A, HOTEL_B), null, "hangisinde spa var mı");
        handler.handle(ctx);
        assertEquals("hangisinde spa var mı", ctx.session().getPendingFacilityQuestion());
    }

    @Test
    void resumeAnswerUsesPendingQuestionAndClearsIt() {
        OrchestrationContext ctx = context(List.of(HOTEL_A, HOTEL_B), "1", "1");
        ctx.session().setPendingFacilityQuestion("havuz var mı"); // asked a turn earlier

        OrchestrationResult r = handler.handle(ctx);

        verify(detailsService).getFeatureDetails("A", 1, "ALL INCLUSIVE");
        // A specific facility is answered deterministically (no LLM), grounded on the PENDING "havuz"
        // question — not the bare "1".
        assertTrue(r.reply().toLowerCase().contains("havuz"), "must answer the pending havuz question");
        verify(chatService, never()).chat(anyString(), nullable(String.class));
        assertNull(ctx.session().getPendingFacilityQuestion(), "pending cleared once answered");
    }

    @Test
    void freshQuestionOverridesStalePendingSoWrongFacilityIsNotAnswered() {
        // Regression: an earlier PET question left pending; a NEW "havuz" question must answer HAVUZ,
        // resolved against the shown list (B=en pahalı), not the stale pet question.
        OrchestrationContext ctx = context(List.of(HOTEL_A, HOTEL_B), null, "en pahalı olanında havuz var mı");
        ctx.session().setPendingFacilityQuestion("hepsinde evcil hayvan var mı");

        OrchestrationResult r = handler.handle(ctx);

        verify(detailsService).getFeatureDetails("B", 7, "HB"); // en pahalı of the shown list
        assertTrue(r.reply().toLowerCase().contains("havuz"), "must answer the CURRENT havuz question");
        assertFalse(r.reply().toLowerCase().contains("evcil hayvan"), "stale pet question must be discarded");
        assertNull(ctx.session().getPendingFacilityQuestion());
    }

    @Test
    void specificFacilityIsAnsweredDeterministicallyWithoutLlm() {
        when(detailsService.getFeatureDetails(anyString(), nullable(Integer.class), nullable(String.class)))
                .thenReturn(new HotelFeatureDetails(new HotelFeaturesDto(false, List.of("pool")), List.of(), List.of()));

        OrchestrationResult r = handler.handle(context(List.of(HOTEL_A, HOTEL_B), "ilk", "ilk otelde havuz var mı"));

        assertTrue(r.reply().contains("Evet") && r.reply().toLowerCase().contains("havuz"),
                "present facility → confident 'var', no model call");
        verify(chatService, never()).chat(anyString(), nullable(String.class));
    }

    @Test
    void openQuestionUsesLlmWithReferenceStripped() {
        // "neler var" has no specific facility group → LLM path, but the relative reference is removed
        // so the model never tries to re-rank by price.
        ArgumentCaptor<String> prompt = ArgumentCaptor.forClass(String.class);
        handler.handle(context(List.of(HOTEL_A, HOTEL_B), null, "en pahalı olanında neler var"));
        verify(chatService).chat(prompt.capture(), nullable(String.class));
        assertFalse(prompt.getValue().contains("en pahalı"), "relative reference must be stripped for the LLM");
    }

    @Test
    void meaninglessAnswerKeepsPendingAndDoesNotResolve() {
        OrchestrationContext ctx = context(List.of(HOTEL_A, HOTEL_B), null, "evet");
        ctx.session().setPendingFacilityQuestion("havuz var mı");

        OrchestrationResult r = handler.handle(ctx);

        assertEquals("havuz var mı", ctx.session().getPendingFacilityQuestion(),
                "a meaningless 'evet' must not wipe the pending question");
        assertTrue(r.reply().contains("Hangi otel"));
        verify(detailsService, never()).getFeatureDetails(anyString(), any(), any());
    }

    @Test
    void namedHotelOutsideFilterIsAnsweredDirectlyWithoutTouchingTheFilter() {
        // Asking about a hotel the filter excluded must NOT trigger a "filtreyi kaldırayım mı?" step and
        // must NOT mutate the shown list (that desynced the UI card panel) — just answer about it.
        List<Object> shown = List.of(HOTEL_A, HOTEL_B);
        OrchestrationContext ctx = context(shown, "melihotel kemer", "melihotel kemer'de havuz var mı");
        ctx.session().setLastApiResultCards(List.of(HOTEL_A, HOTEL_B, HOTEL_C)); // C filtered out

        OrchestrationResult r = handler.handle(ctx);

        assertTrue(r.reply().contains("filtrelenmiş listede yok"), "should say it is outside the filter");
        assertTrue(r.reply().contains("bilgisine bakıyorum"), "and answer it anyway");
        assertTrue(r.reply().toLowerCase().contains("havuz"));
        verify(detailsService).getFeatureDetails("C", 1, "BB"); // resolved from the raw list
        assertEquals(shown, ctx.session().getLastResultCards(), "user's filter must stay untouched");
        assertNull(ctx.session().getPendingFacilityQuestion(), "no confirmation step is pending");
    }

    @Test
    void filterSurvivesSoLaterBulkStillUsesIt() {
        // After an out-of-filter question, a bulk question must still apply to the FILTERED list only.
        List<Object> shown = List.of(HOTEL_A, HOTEL_B);
        OrchestrationContext ctx = context(shown, "melihotel kemer", "melihotel kemer'de havuz var mı");
        ctx.session().setLastApiResultCards(List.of(HOTEL_A, HOTEL_B, HOTEL_C));
        handler.handle(ctx);

        OrchestrationResult bulk = handler.handle(
                new OrchestrationContext(ctx.session(), "hepsinde havuz var mı",
                        IntentType.HOTEL_FACILITY_QA, sel(null)));

        assertTrue(bulk.reply().contains("Rixos Premium") && bulk.reply().contains("TEST KARS2"));
        assertFalse(bulk.reply().contains("melihotel kemer"), "bulk must not include the filtered-out hotel");
    }

    @Test
    void namedHotelUnknownSaysNotFound() {
        OrchestrationContext ctx = context(List.of(HOTEL_A, HOTEL_B), "zzz otel", "zzz otel'de havuz var mı");
        ctx.session().setLastApiResultCards(List.of(HOTEL_A, HOTEL_B));

        OrchestrationResult r = handler.handle(ctx);

        assertTrue(r.reply().contains("bulamadım"));
        verify(detailsService, never()).getFeatureDetails(anyString(), any(), any());
    }

    @Test
    void bulkFreshQuestionOverridesStalePendingBulkQuestion() {
        // Regression: bulk -> bulk. An earlier bulk HAVUZ question left pending; the next bulk PET
        // question must report PET, not re-report the stale havuz.
        when(detailsService.getFeatureDetails(anyString(), nullable(Integer.class), nullable(String.class)))
                .thenReturn(new HotelFeatureDetails(new HotelFeaturesDto(true, List.of("pool")), List.of(), List.of()));
        OrchestrationContext ctx = context(List.of(HOTEL_A, HOTEL_B), null, "hepsinde evcil hayvan kabul ediliyor mu");
        ctx.session().setPendingFacilityQuestion("hepsinde havuz var mı"); // stale bulk question

        OrchestrationResult r = handler.handle(ctx);

        assertTrue(r.reply().contains("Evcil hayvan"), "bulk must answer the CURRENT pet question");
        assertFalse(r.reply().contains("Havuz"), "stale bulk havuz question must not be re-answered");
        assertNull(ctx.session().getPendingFacilityQuestion());
    }

    @Test
    void nameMatchIsCaseAndDiacriticInsensitive() {
        // toLowerCase(tr) maps capital I -> dotless ı, which used to break "MELIHOTEL" vs "melihotel".
        List<Object> cards = List.of(HOTEL_A, HOTEL_C); // HOTEL_C = "melihotel kemer"
        handler.handle(context(cards, "MELIHOTEL KEMER", "MELIHOTEL KEMER'de havuz var mı"));
        verify(detailsService).getFeatureDetails("C", 1, "BB");
    }

    @Test
    void bulkQuestionAnswersEachShownHotel() {
        OrchestrationResult r = handler.handle(
                context(List.of(HOTEL_A, HOTEL_B), null, "hepsinde havuz var mı"));
        assertTrue(r.reply().contains("Havuz"));
        assertTrue(r.reply().contains("Rixos Premium"));
        assertTrue(r.reply().contains("TEST KARS2"));
        verify(detailsService, times(2)).getFeatureDetails(anyString(), any(), any());
    }

    @Test
    void bulkPetQuestionChecksPetFriendlyFlagNotOtherFacilities() {
        // petFriendly lives on its own field, NOT in otherFacilities — the bulk check must read the flag.
        when(detailsService.getFeatureDetails(anyString(), nullable(Integer.class), nullable(String.class)))
                .thenReturn(new HotelFeatureDetails(new HotelFeaturesDto(true, List.of()), List.of(), List.of()));

        OrchestrationResult r = handler.handle(
                context(List.of(HOTEL_A, HOTEL_B), null, "hepsinde evcil hayvan var mı"));

        assertTrue(r.reply().contains("Evcil hayvan"));
        // Both hotels are pet-friendly via the flag → "var", not "görünmüyor".
        assertFalse(r.reply().contains("görünmüyor"), "pet flag true must read as var");
    }

    @Test
    void bulkOverThresholdNudgesToFilter() {
        List<Object> many = new java.util.ArrayList<>();
        for (int i = 0; i < 12; i++) {
            many.add(new HotelProduct("H" + i, "Hotel " + i, "Antalya", 5, new BigDecimal("100"), "EUR",
                    "AI", true, null, List.of(), "off" + i, 1));
        }
        OrchestrationResult r = handler.handle(context(many, null, "hepsinde havuz var mı"));
        assertTrue(r.reply().contains("çok uzun") || r.reply().contains("filtreleyelim"));
        verify(detailsService, never()).getFeatureDetails(anyString(), any(), any());
    }

    /** Builds a SlotCriteria carrying only selectionReference (all other slots null). */
    private static SlotCriteria sel(String reference) {
        try {
            RecordComponent[] components = SlotCriteria.class.getRecordComponents();
            Object[] args = new Object[components.length];
            args[components.length - 1] = reference;
            Class<?>[] parameterTypes = Arrays.stream(components).map(RecordComponent::getType).toArray(Class[]::new);
            return SlotCriteria.class.getDeclaredConstructor(parameterTypes).newInstance(args);
        } catch (ReflectiveOperationException ex) {
            throw new AssertionError("Could not create SlotCriteria for selection reference", ex);
        }
    }
}
