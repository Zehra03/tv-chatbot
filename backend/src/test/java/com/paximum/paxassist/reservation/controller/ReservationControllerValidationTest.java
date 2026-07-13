package com.paximum.paxassist.reservation.controller;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.paximum.paxassist.config.GlobalExceptionHandler;
import com.paximum.paxassist.reservation.service.ReservationService;
import com.paximum.paxassist.reservation.web.ReservationWebMapper;

/**
 * Acceptance test for ticket 5.3: a request with a missing/blank required field must be rejected at
 * the HTTP boundary (Bean Validation via {@code @Valid}) with a 4xx and a meaningful message, and
 * {@link ReservationService} must never be reached. Uses a standalone MockMvc (the module's existing
 * convention, see {@code FlightControllerTest}) so no DB/Redis/security context is required; the
 * {@code @AuthenticationPrincipal} resolver is registered so principal binding does not mask the
 * validation failure.
 */
@ExtendWith(MockitoExtension.class)
class ReservationControllerValidationTest {

    @Mock
    private ReservationService reservationService;

    @Mock
    private ReservationWebMapper mapper;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new ReservationController(reservationService, mapper))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .build();
    }

    @Test
    void preview_missingRequiredFields_isRejectedBeforeService() throws Exception {
        // Empty body: currency/totalAmount/leadGuestName/travellers all missing + no hotel/flight.
        mockMvc.perform(post("/api/v1/reservations/preview")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"));

        verifyNoInteractions(reservationService);
    }

    @Test
    void preview_hotelRoomsExceedAdults_isRejectedBeforeService() throws Exception {
        // Otherwise-valid booking, but 4 rooms for 1 adult violates the cross-field rule
        // (@AssertTrue isRoomsWithinAdults on the Hotel snapshot) — reject before the service.
        String body = """
                {
                  "currency": "EUR",
                  "totalAmount": 100,
                  "leadGuestName": "Ada Lovelace",
                  "travellers": [
                    {"firstName": "Ada", "lastName": "Lovelace", "passengerType": "ADULT"}
                  ],
                  "hotel": {
                    "hotelName": "Grand Antalya",
                    "checkIn": "2026-08-01",
                    "checkOut": "2026-08-05",
                    "rooms": 4,
                    "adults": 1,
                    "children": 0,
                    "price": 100,
                    "currency": "EUR"
                  }
                }
                """;
        mockMvc.perform(post("/api/v1/reservations/preview")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"));

        verifyNoInteractions(reservationService);
    }

    @Test
    void confirm_neitherPreviewIdNorToken_isRejectedBeforeService() throws Exception {
        // @AssertTrue on ConfirmRequest requires exactly one reference; both blank -> reject.
        mockMvc.perform(post("/api/v1/reservations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"previewId\":\"\",\"confirmationToken\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"));

        verifyNoInteractions(reservationService);
    }

    @Test
    void cancel_blankReason_isRejectedBeforeService() throws Exception {
        // @NotBlank reason on CancelRequest.
        mockMvc.perform(patch("/api/v1/reservations/1/cancel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"\",\"serviceIds\":null}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"));

        verifyNoInteractions(reservationService);
    }
}
