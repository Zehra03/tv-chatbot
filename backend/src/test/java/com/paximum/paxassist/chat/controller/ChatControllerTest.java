package com.paximum.paxassist.chat.controller;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import com.paximum.paxassist.auth.security.UserPrincipal;
import com.paximum.paxassist.chat.domain.ChatCaller;
import com.paximum.paxassist.chat.domain.ChatSession;
import com.paximum.paxassist.chat.dto.ChatMessageDto;
import com.paximum.paxassist.chat.dto.ChatSessionDto;
import com.paximum.paxassist.chat.dto.ChatSessionSummaryDto;
import com.paximum.paxassist.chat.dto.PartialCriteriaDto;
import com.paximum.paxassist.chat.dto.ResultCardDto;
import com.paximum.paxassist.chat.service.ChatResponseAssembler;
import com.paximum.paxassist.chat.service.ChatSessionQueryService;
import com.paximum.paxassist.chat.service.ChatSessionStore;
import com.paximum.paxassist.chat.service.ChatViewMapper;
import com.paximum.paxassist.hotel.HotelProduct;
import com.paximum.paxassist.orchestrator.ChatOrchestrationService;
import com.paximum.paxassist.orchestrator.OrchestrationOutcome;
import com.paximum.paxassist.orchestrator.OrchestrationResult;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifies the chat HTTP contract matches the frontend ({@code frontend/src/api/chatApi.ts} +
 * {@code frontend/src/types/chat.ts}): the POST reply is a message OBJECT with typed cards nested
 * under {@code reply.cards}, plus {@code accumulatedCriteria}/{@code pendingQuestion}; the session
 * list and transcript endpoints; and 404 on a missing/foreign session. Uses the REAL assembler +
 * view mapper so the shape mapping is genuinely exercised; only the collaborators are mocked.
 */
@ExtendWith(MockitoExtension.class)
class ChatControllerTest {

    private static final long USER_ID = 7L;
    // A logged-in principal resolves to this caller; session ops are stubbed/verified against it.
    private static final ChatCaller CALLER = ChatCaller.authenticated(USER_ID);

    @Mock
    private ChatOrchestrationService orchestrationService;
    @Mock
    private ChatSessionQueryService sessionQueryService;
    @Mock
    private ChatSessionStore sessionStore;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        UserPrincipal principal = mock(UserPrincipal.class);
        lenient().when(principal.getId()).thenReturn(USER_ID);

        ChatController controller = new ChatController(
                orchestrationService,
                new ChatResponseAssembler(new ChatViewMapper()),
                sessionQueryService,
                sessionStore);

        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setCustomArgumentResolvers(new StubPrincipalResolver(principal))
                .build();
    }

    @Test
    void post_returnsAssistantMessageObjectWithTypedCardsAndCriteria() throws Exception {
        ChatSession session = new ChatSession("42");
        session.setActiveDomain("HOTEL");
        session.setAccumulatedCriteria(new java.util.LinkedHashMap<>(Map.of(
                "location", "Antalya", "checkIn", "2026-08-01", "adults", 2)));
        HotelProduct hotel = new HotelProduct("H1", "Rixos", "Antalya", 5, new BigDecimal("1500"), "EUR", "AI", true);
        OrchestrationResult result = OrchestrationResult
                .cards("Aramanıza uygun 1 otel buldum:", List.of(hotel))
                .withSessionId("42");
        when(orchestrationService.handle("42", "Antalya otel", CALLER))
                .thenReturn(new OrchestrationOutcome(result, session));

        mockMvc.perform(post("/api/v1/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sessionId\":\"42\",\"message\":\"Antalya otel\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessionId").value("42"))
                .andExpect(jsonPath("$.reply.id").isNotEmpty())
                .andExpect(jsonPath("$.reply.role").value("assistant"))
                .andExpect(jsonPath("$.reply.content").value("Aramanıza uygun 1 otel buldum:"))
                .andExpect(jsonPath("$.reply.createdAt").isNotEmpty())
                .andExpect(jsonPath("$.reply.cards[0].productType").value("hotel"))
                .andExpect(jsonPath("$.reply.cards[0].product.hotelName").value("Rixos"))
                .andExpect(jsonPath("$.accumulatedCriteria.intent").value("hotel"))
                .andExpect(jsonPath("$.accumulatedCriteria.criteria.destination").value("Antalya"))
                .andExpect(jsonPath("$.accumulatedCriteria.criteria.adults").value(2));
    }

    @Test
    void post_incompleteTurnCarriesPendingQuestion() throws Exception {
        ChatSession session = new ChatSession("42");
        session.setAccumulatedCriteria(new java.util.LinkedHashMap<>(Map.of("location", "Antalya")));
        OrchestrationResult result = OrchestrationResult
                .clarify("Otele giriş tarihiniz nedir? (örn. 2026-08-01)", "hotel")
                .withSessionId("42");
        when(orchestrationService.handle(eq("42"), eq("Antalya"), eq(CALLER)))
                .thenReturn(new OrchestrationOutcome(result, session));

        mockMvc.perform(post("/api/v1/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sessionId\":\"42\",\"message\":\"Antalya\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reply.content").value("Otele giriş tarihiniz nedir? (örn. 2026-08-01)"))
                .andExpect(jsonPath("$.pendingQuestion").value("Otele giriş tarihiniz nedir? (örn. 2026-08-01)"))
                .andExpect(jsonPath("$.accumulatedCriteria.intent").value("hotel"))
                .andExpect(jsonPath("$.accumulatedCriteria.criteria.destination").value("Antalya"));
    }

    @Test
    void listSessions_returnsSummaryRowsForTheUser() throws Exception {
        when(sessionQueryService.listSummaries(CALLER)).thenReturn(List.of(
                new ChatSessionSummaryDto("42", "Antalya otel", "2026-07-07T10:00:00Z", 4)));

        mockMvc.perform(get("/api/v1/chat/sessions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("42"))
                .andExpect(jsonPath("$[0].title").value("Antalya otel"))
                .andExpect(jsonPath("$[0].updatedAt").value("2026-07-07T10:00:00Z"))
                .andExpect(jsonPath("$[0].messageCount").value(4));
    }

    @Test
    void getSession_returnsFullTranscript() throws Exception {
        ChatMessageDto user = new ChatMessageDto("10", "user", "Antalya otel", "2026-07-07T10:00:00Z", null, null);
        ChatMessageDto assistant = new ChatMessageDto("11", "assistant", "1 otel buldum:", "2026-07-07T10:00:01Z",
                List.of(new ResultCardDto("hotel", Map.of("hotelName", "Rixos"))), null);
        ChatSessionDto dto = new ChatSessionDto("42", "Antalya otel", List.of(user, assistant),
                new PartialCriteriaDto("hotel", Map.of("destination", "Antalya")), null);
        when(sessionQueryService.getSession("42", CALLER)).thenReturn(Optional.of(dto));

        mockMvc.perform(get("/api/v1/chat/42"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("42"))
                .andExpect(jsonPath("$.messages.length()").value(2))
                .andExpect(jsonPath("$.messages[0].role").value("user"))
                .andExpect(jsonPath("$.messages[1].cards[0].productType").value("hotel"))
                .andExpect(jsonPath("$.accumulatedCriteria.intent").value("hotel"));
    }

    @Test
    void getSession_missingOrForeign_returns404() throws Exception {
        when(sessionQueryService.getSession("99", CALLER)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/chat/99"))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteSession_ownedReturns204_otherwise404() throws Exception {
        when(sessionStore.delete("42", CALLER)).thenReturn(true);
        when(sessionStore.delete("99", CALLER)).thenReturn(false);

        mockMvc.perform(delete("/api/v1/chat/42")).andExpect(status().isNoContent());
        mockMvc.perform(delete("/api/v1/chat/99")).andExpect(status().isNotFound());
    }

    // --- Guest path: no principal, identity carried by the X-Guest-Id header ---

    @Test
    void guest_post_resolvesGuestCallerFromHeader() throws Exception {
        ChatSession session = new ChatSession("42");
        OrchestrationResult result = OrchestrationResult.message("Merhaba! Nasıl yardımcı olabilirim?")
                .withSessionId("42");
        when(orchestrationService.handle("42", "merhaba", ChatCaller.guest("guest-xyz")))
                .thenReturn(new OrchestrationOutcome(result, session));

        guestMockMvc().perform(post("/api/v1/chat")
                        .header("X-Guest-Id", "guest-xyz")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sessionId\":\"42\",\"message\":\"merhaba\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessionId").value("42"))
                .andExpect(jsonPath("$.reply.content").value("Merhaba! Nasıl yardımcı olabilirim?"));
    }

    @Test
    void noPrincipalAndNoGuestId_isUnauthorized() throws Exception {
        guestMockMvc().perform(post("/api/v1/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"merhaba\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void overlongGuestId_isBadRequest() throws Exception {
        String tooLong = "g".repeat(65); // guest_token column is varchar(64)
        guestMockMvc().perform(post("/api/v1/chat")
                        .header("X-Guest-Id", tooLong)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"merhaba\"}"))
                .andExpect(status().isBadRequest());
    }

    /** MockMvc whose {@code @AuthenticationPrincipal} resolves to null, so the guest header path runs. */
    private MockMvc guestMockMvc() {
        ChatController controller = new ChatController(
                orchestrationService,
                new ChatResponseAssembler(new ChatViewMapper()),
                sessionQueryService,
                sessionStore);
        return MockMvcBuilders.standaloneSetup(controller)
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .build();
    }

    /** Supplies the {@code @AuthenticationPrincipal} argument in standalone MockMvc (no security context). */
    private record StubPrincipalResolver(UserPrincipal principal) implements HandlerMethodArgumentResolver {
        @Override
        public boolean supportsParameter(MethodParameter parameter) {
            return UserPrincipal.class.equals(parameter.getParameterType());
        }

        @Override
        public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
                                      NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
            return principal;
        }
    }
}
