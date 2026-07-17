package com.paximum.paxassist.orchestrator.intent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

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
import com.paximum.paxassist.orchestrator.OrchestrationContext;
import com.paximum.paxassist.orchestrator.OrchestrationResult;
import com.paximum.paxassist.validator.ValidationOrchestrator;

/**
 * Unit tests for {@link HotelFacilityQaHandler}'s deterministic core: resolving which hotel the user
 * means (ordinal / name / ambiguous / no results) and calling the detail service with the right
 * id + provider + board. The LLM + validator are mocked (the validator is made to throw so the
 * handler's fail-open path returns the generated answer verbatim).
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

    @BeforeEach
    void setUp() {
        detailsService = mock(HotelDetailsService.class);
        chatService = mock(ChatService.class);
        validator = mock(ValidationOrchestrator.class);
        greetingNameService = mock(GreetingNameService.class);
        handler = new HotelFacilityQaHandler(detailsService, chatService, validator, greetingNameService);

        when(greetingNameService.firstNameOf(nullable(Long.class))).thenReturn(Optional.empty());
        when(detailsService.getFeatureDetails(anyString(), nullable(Integer.class), nullable(String.class)))
                .thenReturn(new HotelFeatureDetails(new HotelFeaturesDto(false, List.of()), List.of(), List.of()));
        when(chatService.chat(anyString(), nullable(String.class))).thenReturn(new AiReply("stub answer"));
        // Force the fail-open path so the handler returns the generated answer without constructing a ValidationOutcome.
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
    void nonHotelResultsAreRejected() {
        OrchestrationResult r = handler.handle(context(List.of("a flight card"), "ilk"));
        assertTrue(r.reply().contains("yalnızca oteller"));
        verify(detailsService, never()).getFeatureDetails(anyString(), any(), any());
    }

    @Test
    void unresolvedReferenceRemembersQuestionForResume() {
        OrchestrationContext ctx = context(List.of(HOTEL_A, HOTEL_B), null, "hangisinde spa var");
        handler.handle(ctx);
        // The original question is stored so the next "1" answer can resume it.
        assertEquals("hangisinde spa var", ctx.session().getPendingFacilityQuestion());
    }

    @Test
    void resumeAnswerUsesPendingQuestionAndClearsIt() {
        OrchestrationContext ctx = context(List.of(HOTEL_A, HOTEL_B), "1", "1");
        ctx.session().setPendingFacilityQuestion("havuz var mı"); // asked a turn earlier
        ArgumentCaptor<String> prompt = ArgumentCaptor.forClass(String.class);

        handler.handle(ctx);

        verify(detailsService).getFeatureDetails("A", 1, "ALL INCLUSIVE");
        verify(chatService).chat(prompt.capture(), nullable(String.class));
        assertTrue(prompt.getValue().contains("havuz var mı"),
                "answer must be grounded on the ORIGINAL question, not the bare '1'");
        assertNull(ctx.session().getPendingFacilityQuestion(), "pending cleared once answered");
    }

    @Test
    void bulkQuestionGetsGracefulMessageNotALoop() {
        OrchestrationResult r = handler.handle(
                context(List.of(HOTEL_A, HOTEL_B), null, "hepsinde havuz var mı"));
        assertTrue(r.reply().contains("tek tek"));
        // Still remembered, so a following "1" resumes instead of restarting.
        // (getFeatureDetails not called yet — no single hotel picked.)
        verify(detailsService, never()).getFeatureDetails(anyString(), any(), any());
    }

    /** Builds a SlotCriteria carrying only selectionReference (all 26 other slots null). */
    private static SlotCriteria sel(String reference) {
        return new SlotCriteria(
                null, null, null, null, null, null, null, null, null, null, // hotel (10)
                null, null, null, null, null, null, null, null, null,        // flight (9)
                null, null, null, null, null,                                 // shared (5)
                null, null,                                                   // filter/sort (2)
                reference);                                                   // selectionReference
    }
}
