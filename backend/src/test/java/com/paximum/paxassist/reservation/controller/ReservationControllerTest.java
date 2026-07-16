package com.paximum.paxassist.reservation.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import com.paximum.paxassist.auth.security.UserPrincipal;
import com.paximum.paxassist.config.GlobalExceptionHandler;
import com.paximum.paxassist.reservation.domain.ProductType;
import com.paximum.paxassist.reservation.domain.Reservation;
import com.paximum.paxassist.reservation.domain.ReservationStatus;
import com.paximum.paxassist.reservation.service.CancelResult;
import com.paximum.paxassist.reservation.service.ConfirmationResult;
import com.paximum.paxassist.reservation.service.PreviewResult;
import com.paximum.paxassist.reservation.service.ReservationDetailResult;
import com.paximum.paxassist.reservation.service.ReservationPreview;
import com.paximum.paxassist.reservation.service.ReservationService;
import com.paximum.paxassist.reservation.service.command.PreviewReservationCommand;
import com.paximum.paxassist.reservation.web.ReservationWebMapper;
import com.paximum.paxassist.reservation.web.dto.PreviewResponse;
import com.paximum.paxassist.reservation.web.dto.ReservationDetailResponse;
import com.paximum.paxassist.reservation.web.dto.ReservationSummaryResponse;

@ExtendWith(MockitoExtension.class)
class ReservationControllerTest {

    @Mock
    private ReservationService reservationService;

    @Mock
    private ReservationWebMapper mapper;

    private MockMvc mockMvc;
    private UserPrincipal mockPrincipal;

    @BeforeEach
    void setUp() {
        // Mock UserPrincipal
        mockPrincipal = org.mockito.Mockito.mock(UserPrincipal.class);
        // Lenient: a request rejected by @Valid never reaches the controller body, so the id is unused there.
        org.mockito.Mockito.lenient().when(mockPrincipal.getId()).thenReturn(123L);

        // Custom resolver to inject the mock principal when @AuthenticationPrincipal is used
        HandlerMethodArgumentResolver principalResolver = new HandlerMethodArgumentResolver() {
            @Override
            public boolean supportsParameter(MethodParameter parameter) {
                return parameter.getParameterAnnotation(AuthenticationPrincipal.class) != null;
            }
            @Override
            public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer, NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
                return mockPrincipal;
            }
        };

        mockMvc = MockMvcBuilders.standaloneSetup(new ReservationController(reservationService, mapper))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(principalResolver)
                .build();
    }

    /**
     * A valid booking body. The traveller list must match the snapshot's party size (1 adult ->
     * 1 traveller) and the stay must be in the future, so the dates are derived from today.
     */
    private String previewBody() {
        java.time.LocalDate checkIn = java.time.LocalDate.now().plusDays(30);
        return """
                {
                    "currency": "EUR",
                    "totalAmount": 1500.00,
                    "leadGuestName": "John Doe",
                    "travellers": [{"firstName":"John","lastName":"Doe","passengerType":"ADULT",
                                    "email":"john@example.com","phone":"+905551112233"}],
                    "hotel": {
                        "hotelName":"Hotel A",
                        "checkIn":"%s",
                        "checkOut":"%s",
                        "rooms":1,
                        "adults":1,
                        "price":1500.00,
                        "currency":"EUR"
                    }
                }
                """.formatted(checkIn, checkIn.plusDays(4));
    }

    @Test
    void preview_validRequest_routesToServiceAndReturnsMappedResponse() throws Exception {
        ReservationPreview previewResult = new ReservationPreview("preview-123", java.time.Instant.now(),
                ProductType.HOTEL, java.math.BigDecimal.valueOf(1500), "EUR", "John Doe",
                List.of("John Doe"), true, false, false, null, true, null, null);
        when(reservationService.previewReservation(any(PreviewReservationCommand.class)))
                .thenReturn(new PreviewResult.Priced(previewResult));

        PreviewResponse previewResponse = new PreviewResponse(
                "preview-123", java.time.Instant.now(), ProductType.HOTEL, java.math.BigDecimal.valueOf(1500),
                "EUR", "John Doe", List.of("John Doe"), true, false, false, null, true, null, null);
        when(mapper.toPreviewResponse(previewResult)).thenReturn(previewResponse);

        mockMvc.perform(post("/api/v1/reservations/preview")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(previewBody()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.previewId").value("preview-123"))
                .andExpect(jsonPath("$.priceChanged").value(false));

        verify(reservationService).previewReservation(any(PreviewReservationCommand.class));
    }

    @Test
    void confirm_withPreviewId_routesToServiceConfirm() throws Exception {
        // Given
        String requestJson = """
                {
                    "previewId": "preview-123"
                }
                """;
        
        when(reservationService.confirmReservation("preview-123", 123L)).thenReturn(new ConfirmationResult.OwnershipMismatch());

        // When & Then
        mockMvc.perform(post("/api/v1/reservations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.outcome").value("OWNERSHIP_MISMATCH"));

        verify(reservationService).confirmReservation("preview-123", 123L);
    }

    // =============================================================================================
    // POST /api/v1/reservations — every ConfirmationResult branch. This is the only call that can
    // trigger a real TourVisio purchase, so each outcome must reach the client as its own status:
    // a retryable failure must never look like a success, and an ambiguous commit must never look
    // like a clean rejection.
    // =============================================================================================

    private void confirmWithPreviewId(ConfirmationResult result) {
        when(reservationService.confirmReservation("preview-123", 123L)).thenReturn(result);
    }

    private org.springframework.test.web.servlet.ResultActions postConfirm(String body) throws Exception {
        return mockMvc.perform(post("/api/v1/reservations")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private static final String PREVIEW_BODY = """
            {"previewId": "preview-123"}
            """;

    @Test
    void confirm_confirmed_returns201WithTheCreatedReservation() throws Exception {
        confirmWithPreviewId(new ConfirmationResult.Confirmed(7L, "PAX-20260714-A1B2C3", "TV-99"));
        Reservation saved = org.mockito.Mockito.mock(Reservation.class);
        when(reservationService.getReservation(7L)).thenReturn(java.util.Optional.of(saved));
        when(mapper.toSummary(saved)).thenReturn(new ReservationSummaryResponse(
                7L, "PAX-20260714-A1B2C3", "TV-99", ReservationStatus.CONFIRMED, ProductType.HOTEL,
                java.time.LocalDate.now(), new java.math.BigDecimal("1500.00"), "EUR", "Ada Yılmaz"));

        postConfirm(PREVIEW_BODY)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(7))
                .andExpect(jsonPath("$.reservationNumber").value("PAX-20260714-A1B2C3"))
                .andExpect(jsonPath("$.status").value("confirmed"))
                .andExpect(jsonPath("$.totalAmount").value(1500.00));
    }

    @Test
    void confirm_confirmedButReloadFails_stillReports201AndTheReservationNumber() throws Exception {
        // The purchase happened; a failed re-read must not be reported as a failed booking.
        confirmWithPreviewId(new ConfirmationResult.Confirmed(7L, "PAX-20260714-A1B2C3", "TV-99"));
        when(reservationService.getReservation(7L)).thenReturn(java.util.Optional.empty());

        postConfirm(PREVIEW_BODY)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.outcome").value("CONFIRMED"))
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.containsString("PAX-20260714-A1B2C3")));
    }

    @Test
    void confirm_needsUserConfirmation_returns200WithTokenAndWarnings() throws Exception {
        // TourVisio flagged e.g. DuplicateReservationFound: NOT committed, awaiting a second confirm.
        confirmWithPreviewId(new ConfirmationResult.NeedsUserConfirmation(
                "token-abc", List.of("DuplicateReservationFound")));

        postConfirm(PREVIEW_BODY)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.confirmationToken").value("token-abc"))
                .andExpect(jsonPath("$.warnings[0]").value("DuplicateReservationFound"));
    }

    @Test
    void confirm_withConfirmationToken_resumesAtCommitInsteadOfStartingOver() throws Exception {
        when(reservationService.confirmReservationAfterWarning("token-abc", 123L))
                .thenReturn(new ConfirmationResult.Confirmed(7L, "PAX-1", "TV-99"));
        when(reservationService.getReservation(7L)).thenReturn(java.util.Optional.empty());

        postConfirm("""
                {"confirmationToken": "token-abc"}
                """)
                .andExpect(status().isCreated());

        verify(reservationService).confirmReservationAfterWarning("token-abc", 123L);
        // The preview path must not run as well — that would begin a second TourVisio transaction.
        verify(reservationService, org.mockito.Mockito.never()).confirmReservation(any(), any());
    }

    @Test
    void confirm_previewExpired_returns410() throws Exception {
        confirmWithPreviewId(new ConfirmationResult.PreviewExpired());

        postConfirm(PREVIEW_BODY)
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.outcome").value("PREVIEW_EXPIRED"));
    }

    @Test
    void confirm_duplicateInProgress_returns409() throws Exception {
        // The atomic claim was lost — a concurrent/double click is already confirming this preview.
        confirmWithPreviewId(new ConfirmationResult.DuplicateInProgress());

        postConfirm(PREVIEW_BODY)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.outcome").value("DUPLICATE_IN_PROGRESS"));
    }

    @Test
    void confirm_tourVisioRejected_returns422WithAUserFacingMessageNotTheProvidersOwn() throws Exception {
        // The provider's text is written for an integrator, not a traveller: the user gets a plain
        // sentence that also states the important fact — nothing was purchased.
        confirmWithPreviewId(new ConfirmationResult.TourVisioRejected(
                "commitTransaction", "ERR_4021", "OfferPriceMismatchException at BookingService.commit"));

        postConfirm(PREVIEW_BODY)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.outcome").value("TOURVISIO_REJECTED"))
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.containsString("Rezervasyon yapılmadı")));
    }

    @Test
    void confirm_tourVisioUnavailable_returns502() throws Exception {
        confirmWithPreviewId(new ConfirmationResult.TourVisioUnavailable("beginTransaction", "read timed out"));

        postConfirm(PREVIEW_BODY)
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.outcome").value("TOURVISIO_UNAVAILABLE"));
    }

    @Test
    void confirm_commitOutcomeUnknown_returns202AndNotAFailure() throws Exception {
        // The purchase MAY exist. Reporting this as a clean failure would invite a double booking.
        confirmWithPreviewId(new ConfirmationResult.CommitOutcomeUnknown("TV-99", "read timed out after commit"));

        postConfirm(PREVIEW_BODY)
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.outcome").value("COMMIT_OUTCOME_UNKNOWN"));
    }

    @Test
    void confirm_orphanedBooking_returns500() throws Exception {
        // Bought on TourVisio, not persisted locally — needs manual reconciliation.
        confirmWithPreviewId(new ConfirmationResult.OrphanedBooking("TV-99", "flagged for reconciliation"));

        postConfirm(PREVIEW_BODY)
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.outcome").value("ORPHANED_BOOKING"));
    }

    @Test
    void confirm_priceMismatch_returns409WithBothAmounts() throws Exception {
        // Nothing was purchased. The user must see what they agreed to and what it costs now.
        confirmWithPreviewId(new ConfirmationResult.PriceMismatch(
                new java.math.BigDecimal("1.00"), new java.math.BigDecimal("1500.00"), "EUR"));

        postConfirm(PREVIEW_BODY)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.outcome").value("PRICE_MISMATCH"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.allOf(
                        org.hamcrest.Matchers.containsString("1.00"),
                        org.hamcrest.Matchers.containsString("1500.00"),
                        org.hamcrest.Matchers.containsString("Rezervasyon yapılmadı"))));
    }

    @Test
    void confirm_blankPreviewIdAndToken_isRejectedBeforeTheService() throws Exception {
        postConfirm("""
                {"previewId": "", "confirmationToken": ""}
                """)
                .andExpect(status().isBadRequest());

        org.mockito.Mockito.verifyNoInteractions(reservationService);
    }

    /**
     * Technical detail must never reach the traveller: no "read timed out", no host/port, no provider
     * internals. The outcome CODE is what the frontend branches on; the message is what the user reads.
     * Each of these results carries an internal description — none of it may appear in the body.
     */
    @Test
    void confirm_failureMessages_leakNoInternalDetail() throws Exception {
        record Case(ConfirmationResult result, int status, String secret) {}
        List<Case> cases = List.of(
                new Case(new ConfirmationResult.TourVisioUnavailable("beginTransaction",
                        "Connection refused: tourvisio-db:5432 read timed out"), 502, "5432"),
                new Case(new ConfirmationResult.CommitOutcomeUnknown("TV-99",
                        "java.net.SocketTimeoutException at RestClientTourVisioBookingClient"), 202, "SocketTimeoutException"),
                new Case(new ConfirmationResult.OrphanedBooking("TV-99",
                        "SQLException: relation \"reservations\" violates constraint"), 500, "SQLException"),
                new Case(new ConfirmationResult.TourVisioRejected("commitTransaction", "ERR_501",
                        "Internal provider stack: com.tourvisio.Booking.commit()"), 422, "com.tourvisio"));

        for (Case c : cases) {
            org.mockito.Mockito.reset(reservationService);
            when(reservationService.confirmReservation("preview-123", 123L)).thenReturn(c.result());

            String body = postConfirm(PREVIEW_BODY)
                    .andExpect(status().is(c.status()))
                    .andReturn().getResponse().getContentAsString();

            assertThat(body).doesNotContain(c.secret());
            assertThat(body).doesNotContain("timed out", "Exception", "Connection refused");
        }
    }

    @Test
    void cancel_failureMessages_leakNoInternalDetail() throws Exception {
        when(reservationService.cancelReservation(eq(1L), eq(123L), any(), any()))
                .thenReturn(new CancelResult.TourVisioUnavailable("Connection refused: 10.0.0.5:8443"));

        String body = mockMvc.perform(patch("/api/v1/reservations/1/cancel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Vazgeçtim\"}"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.outcome").value("TOURVISIO_UNAVAILABLE"))
                .andReturn().getResponse().getContentAsString();

        assertThat(body).doesNotContain("10.0.0.5", "Connection refused");
    }

    // =============================================================================================
    // POST /api/v1/reservations/preview — K21: re-priced and re-checked against TourVisio.
    // =============================================================================================

    @Test
    void preview_soldOutProduct_returns409WithAPlainExplanation() throws Exception {
        when(reservationService.previewReservation(any()))
                .thenReturn(new PreviewResult.Unavailable("OfferNotAvailable"));

        mockMvc.perform(post("/api/v1/reservations/preview")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(previewBody()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.outcome").value("PRODUCT_UNAVAILABLE"))
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.containsString("artık müsait değil")));
    }

    @Test
    void preview_providerUnavailable_returns502AndLeaksNothing() throws Exception {
        when(reservationService.previewReservation(any()))
                .thenReturn(new PreviewResult.ProviderUnavailable("read timed out at tourvisio:443"));

        String body = mockMvc.perform(post("/api/v1/reservations/preview")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(previewBody()))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.outcome").value("PROVIDER_UNAVAILABLE"))
                .andReturn().getResponse().getContentAsString();

        assertThat(body).doesNotContain("tourvisio:443", "timed out");
    }

    @Test
    void preview_priceChanged_handsBothAmountsToTheUi() throws Exception {
        ReservationPreview preview = new ReservationPreview("preview-1", java.time.Instant.now(),
                ProductType.HOTEL, new java.math.BigDecimal("1750.00"), "EUR", "Ada Yılmaz",
                List.of("Ada Yılmaz"), true, false, true, new java.math.BigDecimal("1500.00"), true, null, null);
        when(reservationService.previewReservation(any())).thenReturn(new PreviewResult.Priced(preview));
        when(mapper.toPreviewResponse(preview)).thenReturn(new PreviewResponse("preview-1",
                java.time.Instant.now(), ProductType.HOTEL, new java.math.BigDecimal("1750.00"), "EUR",
                "Ada Yılmaz", List.of("Ada Yılmaz"), true, false, true, new java.math.BigDecimal("1500.00"), true, null, null));

        mockMvc.perform(post("/api/v1/reservations/preview")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(previewBody()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.priceChanged").value(true))
                .andExpect(jsonPath("$.previousAmount").value(1500.00))
                .andExpect(jsonPath("$.totalAmount").value(1750.00));
    }

    // =============================================================================================
    // GET /api/v1/reservations/{id}
    // =============================================================================================

    @Test
    void detail_returnsTheReservationForItsOwner() throws Exception {
        Reservation reservation = org.mockito.Mockito.mock(Reservation.class);
        when(reservationService.getReservationDetail(5L, 123L))
                .thenReturn(java.util.Optional.of(new ReservationDetailResult(reservation, List.of())));
        when(mapper.toDetail(eq(reservation), any())).thenReturn(new ReservationDetailResponse(
                5L, "PAX-1", "TV-99", ReservationStatus.CONFIRMED, ProductType.HOTEL,
                java.time.LocalDate.now(), new java.math.BigDecimal("1500.00"), "EUR", "Ada Yılmaz",
                List.of(), null, null, List.of()));

        mockMvc.perform(get("/api/v1/reservations/5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(5))
                .andExpect(jsonPath("$.reservationNumber").value("PAX-1"));
    }

    @Test
    void detail_ofAnotherUsersReservation_is404NotForbidden() throws Exception {
        // The service reports a non-owned reservation as absent, so existence is not revealed.
        when(reservationService.getReservationDetail(5L, 123L)).thenReturn(java.util.Optional.empty());

        mockMvc.perform(get("/api/v1/reservations/5"))
                .andExpect(status().isNotFound());
    }

    @Test
    void list_routesToServiceAndMapsResults() throws Exception {
        // Given
        when(reservationService.listReservations(123L)).thenReturn(List.of());

        // When & Then
        mockMvc.perform(get("/api/v1/reservations"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());

        verify(reservationService).listReservations(123L);
    }

    @Test
    void cancel_validRequest_routesToService() throws Exception {
        // Given
        String requestJson = """
                {
                    "reason": "Changed my mind"
                }
                """;

        when(reservationService.cancelReservation(eq(1L), eq(123L), eq("Changed my mind"), any()))
                .thenReturn(new CancelResult.NotFound());

        // When & Then
        mockMvc.perform(patch("/api/v1/reservations/1/cancel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isNotFound());

        verify(reservationService).cancelReservation(eq(1L), eq(123L), eq("Changed my mind"), any());
    }
}
