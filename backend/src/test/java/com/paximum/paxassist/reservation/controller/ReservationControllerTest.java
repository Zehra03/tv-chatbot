package com.paximum.paxassist.reservation.controller;

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
import com.paximum.paxassist.reservation.service.CancelResult;
import com.paximum.paxassist.reservation.service.ConfirmationResult;
import com.paximum.paxassist.reservation.service.ReservationPreview;
import com.paximum.paxassist.reservation.service.ReservationService;
import com.paximum.paxassist.reservation.service.command.PreviewReservationCommand;
import com.paximum.paxassist.reservation.web.ReservationWebMapper;
import com.paximum.paxassist.reservation.web.dto.PreviewResponse;

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
        when(mockPrincipal.getId()).thenReturn(123L);

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

    @Test
    void preview_validRequest_routesToServiceAndReturnsMappedResponse() throws Exception {
        // Given
        String requestJson = """
                {
                    "currency": "EUR",
                    "totalAmount": 1500.00,
                    "leadGuestName": "John Doe",
                    "travellers": [{"firstName":"John","lastName":"Doe","passengerType":"ADULT"}],
                    "hotel": {
                        "hotelName":"Hotel A",
                        "checkIn":"2026-08-01",
                        "checkOut":"2026-08-05",
                        "rooms":1,
                        "adults":2,
                        "price":1500.00,
                        "currency":"EUR"
                    }
                }
                """;

        ReservationPreview previewResult = new ReservationPreview("preview-123", java.time.Instant.now(), ProductType.HOTEL, java.math.BigDecimal.valueOf(1500), "EUR", "John Doe", List.of("John Doe"), true, false);
        when(reservationService.previewReservation(any(PreviewReservationCommand.class))).thenReturn(previewResult);
        
        PreviewResponse previewResponse = new PreviewResponse(
                "preview-123", java.time.Instant.now(), ProductType.HOTEL, java.math.BigDecimal.valueOf(1500),
                "EUR", "John Doe", List.of("John Doe"), true, false);
        when(mapper.toPreviewResponse(previewResult)).thenReturn(previewResponse);

        // When & Then
        mockMvc.perform(post("/api/v1/reservations/preview")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.previewId").value("preview-123"));

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
