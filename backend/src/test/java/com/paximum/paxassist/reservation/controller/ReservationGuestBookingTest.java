package com.paximum.paxassist.reservation.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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

import com.paximum.paxassist.config.GlobalExceptionHandler;
import com.paximum.paxassist.reservation.domain.ProductType;
import com.paximum.paxassist.reservation.domain.Reservation;
import com.paximum.paxassist.reservation.domain.ReservationCaller;
import com.paximum.paxassist.reservation.domain.ReservationStatus;
import com.paximum.paxassist.reservation.service.ConfirmationResult;
import com.paximum.paxassist.reservation.service.PreviewResult;
import com.paximum.paxassist.reservation.service.ReservationPreview;
import com.paximum.paxassist.reservation.service.ReservationService;
import com.paximum.paxassist.reservation.service.command.PreviewReservationCommand;
import com.paximum.paxassist.reservation.web.ReservationWebMapper;
import com.paximum.paxassist.reservation.web.dto.PreviewResponse;
import com.paximum.paxassist.reservation.web.dto.ReservationDetailResponse;

/**
 * The unauthenticated (guest) side of the reservation API: booking without an account, and getting
 * back to that booking by PNR + surname.
 *
 * <p>The principal resolver deliberately returns {@code null} here — that is what
 * {@code @AuthenticationPrincipal} yields on a {@code permitAll} endpoint reached without a token,
 * and it is the exact condition the sibling {@link ReservationControllerTest} (always logged in)
 * cannot exercise.
 */
@ExtendWith(MockitoExtension.class)
class ReservationGuestBookingTest {

    private static final String GUEST_ID = "guest-abc-123";

    @Mock
    private ReservationService reservationService;

    @Mock
    private ReservationWebMapper mapper;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        HandlerMethodArgumentResolver anonymousPrincipal = new HandlerMethodArgumentResolver() {
            @Override
            public boolean supportsParameter(MethodParameter parameter) {
                return parameter.getParameterAnnotation(AuthenticationPrincipal.class) != null;
            }

            @Override
            public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
                    NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
                return null;
            }
        };

        mockMvc = MockMvcBuilders.standaloneSetup(new ReservationController(reservationService, mapper))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(anonymousPrincipal)
                .build();
    }

    @Test
    void preview_asGuest_ownsTheSnapshotWithTheGuestToken() throws Exception {
        when(reservationService.previewReservation(any())).thenReturn(new PreviewResult.Priced(preview()));
        when(mapper.toPreviewResponse(any())).thenReturn(previewResponse());

        mockMvc.perform(post("/api/v1/reservations/preview")
                        .header("X-Guest-Id", GUEST_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(previewBody()))
                .andExpect(status().isOk());

        ArgumentCaptor<PreviewReservationCommand> captor = ArgumentCaptor.forClass(PreviewReservationCommand.class);
        verify(reservationService).previewReservation(captor.capture());
        // The guest owns the preview through the header, and no user id is invented for them.
        assertThat(captor.getValue().guestToken()).isEqualTo(GUEST_ID);
        assertThat(captor.getValue().userId()).isNull();
    }

    @Test
    void confirm_asGuest_passesTheGuestCallerThrough() throws Exception {
        when(reservationService.confirmReservation(eq("preview-1"), any()))
                .thenReturn(new ConfirmationResult.Confirmed(7L, "PAX-20260721-ABC123", "RC00777"));
        when(reservationService.getReservation(7L)).thenReturn(Optional.empty());

        mockMvc.perform(post("/api/v1/reservations")
                        .header("X-Guest-Id", GUEST_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"previewId\":\"preview-1\"}"))
                .andExpect(status().isCreated());

        verify(reservationService).confirmReservation("preview-1", ReservationCaller.guest(GUEST_ID));
    }

    @Test
    void preview_withNoIdentityAtAll_isUnauthorizedAndNeverReachesTheService() throws Exception {
        // A preview owned by nobody could be confirmed — i.e. purchased — by anybody.
        mockMvc.perform(post("/api/v1/reservations/preview")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(previewBody()))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(reservationService);
    }

    @Test
    void preview_withAnOverlongGuestId_isRejectedRatherThanTruncated() throws Exception {
        // 65 chars: one past guest_token varchar(64). Truncating would silently merge two visitors.
        mockMvc.perform(post("/api/v1/reservations/preview")
                        .header("X-Guest-Id", "g".repeat(65))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(previewBody()))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(reservationService);
    }

    @Test
    void lookup_withMatchingPnrAndSurname_returnsTheReservation() throws Exception {
        Reservation reservation = new Reservation();
        reservation.setReservationNumber("PAX-20260721-ABC123");
        when(reservationService.lookupReservation("PAX-20260721-ABC123", "Yılmaz"))
                .thenReturn(Optional.of(reservation));
        when(mapper.toDetail(eq(reservation), any())).thenReturn(detailResponse("PAX-20260721-ABC123"));

        mockMvc.perform(get("/api/v1/reservations/lookup")
                        .param("pnr", "PAX-20260721-ABC123")
                        .param("surname", "Yılmaz"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reservationNumber").value("PAX-20260721-ABC123"));
    }

    @Test
    void lookup_withTheWrongSurname_is404AndSaysNothingAboutThePnr() throws Exception {
        // A distinct "PNR exists, wrong surname" answer would turn this public endpoint into a PNR
        // oracle — and the successful body carries passenger contact details.
        when(reservationService.lookupReservation("PAX-20260721-ABC123", "Wrong")).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/reservations/lookup")
                        .param("pnr", "PAX-20260721-ABC123")
                        .param("surname", "Wrong"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.outcome").value("RESERVATION_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("soyad hatalı"))));
    }

    // --- fixtures ------------------------------------------------------------------------------

    private String previewBody() {
        LocalDate checkIn = LocalDate.now().plusDays(30);
        return """
                {
                    "currency": "EUR",
                    "totalAmount": 1500.00,
                    "leadGuestName": "Ada Yılmaz",
                    "travellers": [{"firstName":"Ada","lastName":"Yılmaz","passengerType":"ADULT",
                                    "email":"ada@example.com","phone":"+905551112233"}],
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

    private ReservationPreview preview() {
        return new ReservationPreview("preview-1", Instant.now().plusSeconds(900), ProductType.HOTEL,
                new BigDecimal("1500.00"), "EUR", "Ada Yılmaz", List.of("Ada Yılmaz"),
                true, false, false, null, null, true, null, null);
    }

    private PreviewResponse previewResponse() {
        return new PreviewResponse("preview-1", Instant.now().plusSeconds(900), ProductType.HOTEL,
                new BigDecimal("1500.00"), "EUR", "Ada Yılmaz", List.of("Ada Yılmaz"),
                true, false, false, null, null, true, null, null);
    }

    private ReservationDetailResponse detailResponse(String reservationNumber) {
        return new ReservationDetailResponse(1L, reservationNumber, "RC00777", ReservationStatus.CONFIRMED,
                ProductType.HOTEL, LocalDate.now(), new BigDecimal("1500.00"), "EUR", "Ada Yılmaz",
                List.of(), null, null, List.of());
    }
}
