package com.paximum.paxassist.reservation.controller;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;

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

    /** Hotel checkIn carries @FutureOrPresent, so fixture dates are derived from today, never pinned. */
    private static final LocalDate CHECK_IN = LocalDate.now().plusDays(30);

    /** A hotel booking body: the traveller list and the snapshot's party size are the parts under test. */
    private String hotelBooking(String travellersJson, int rooms, int adults, int children) {
        return """
                {
                  "currency": "EUR",
                  "totalAmount": 100,
                  "leadGuestName": "Ada Lovelace",
                  "travellers": [%s],
                  "hotel": {
                    "hotelName": "Grand Antalya",
                    "checkIn": "%s",
                    "checkOut": "%s",
                    "rooms": %d,
                    "adults": %d,
                    "children": %d,
                    "price": 100,
                    "currency": "EUR"
                  }
                }
                """.formatted(travellersJson, CHECK_IN, CHECK_IN.plusDays(4), rooms, adults, children);
    }

    private void expectRejectedBeforeService(String body) throws Exception {
        mockMvc.perform(post("/api/v1/reservations/preview")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"));

        // The whole point: TourVisio is never reached, and no preview is burned.
        verifyNoInteractions(reservationService);
    }

    @Test
    void preview_hotelRoomsExceedAdults_isRejectedBeforeService() throws Exception {
        // Otherwise-valid booking, but 4 rooms for 1 adult violates the cross-field rule
        // (@AssertTrue isRoomsWithinAdults on the Hotel snapshot) — reject before the service.
        expectRejectedBeforeService(hotelBooking(
                """
                {"firstName": "Ada", "lastName": "Lovelace", "passengerType": "ADULT"}
                """, 4, 1, 0));
    }

    @Test
    void preview_childOnlyBooking_isRejectedBeforeService() throws Exception {
        // K: "Tek çocukla preview isteği 400 + anlaşılır mesaj döner; TourVisio'ya hiç gidilmez."
        expectRejectedBeforeService(hotelBooking(
                """
                {"firstName": "Max", "lastName": "Yılmaz", "passengerType": "CHILD", "age": 7}
                """, 1, 0, 1));
    }

    @Test
    void preview_childAsLeadGuest_isRejectedBeforeService() throws Exception {
        // Contact details land on the first traveller, so a 7-year-old must not be the lead guest.
        expectRejectedBeforeService(hotelBooking(
                """
                {"firstName": "Max", "lastName": "Yılmaz", "passengerType": "CHILD", "age": 7},
                {"firstName": "Ada", "lastName": "Yılmaz", "passengerType": "ADULT", "age": 34}
                """, 1, 1, 1));
    }

    @Test
    void preview_ageContradictingPassengerType_isRejectedBeforeService() throws Exception {
        // A 45-year-old declared CHILD to claim child pricing.
        expectRejectedBeforeService(hotelBooking(
                """
                {"firstName": "Ada", "lastName": "Yılmaz", "passengerType": "ADULT", "age": 34},
                {"firstName": "Max", "lastName": "Yılmaz", "passengerType": "CHILD", "age": 45}
                """, 1, 1, 1));
    }

    @Test
    void preview_moreTravellersThanTheProductWasPricedFor_isRejectedBeforeService() throws Exception {
        // K: "API'den 5 yolcu gönderilirse 400" — the hotel here was priced for 2 people.
        expectRejectedBeforeService(hotelBooking(
                """
                {"firstName": "A", "lastName": "Yılmaz", "passengerType": "ADULT", "age": 34},
                {"firstName": "B", "lastName": "Yılmaz", "passengerType": "ADULT", "age": 34},
                {"firstName": "C", "lastName": "Yılmaz", "passengerType": "ADULT", "age": 34},
                {"firstName": "D", "lastName": "Yılmaz", "passengerType": "ADULT", "age": 34},
                {"firstName": "E", "lastName": "Yılmaz", "passengerType": "ADULT", "age": 34}
                """, 1, 2, 0));
    }

    @Test
    void preview_leadGuestWithoutContactDetails_isRejectedBeforeService() throws Exception {
        // Required-field check: no e-mail / no phone on the lead guest -> the booking cannot proceed.
        expectRejectedBeforeService(hotelBooking(
                """
                {"firstName": "Ada", "lastName": "Yılmaz", "passengerType": "ADULT", "age": 34}
                """, 1, 1, 0));
    }

    @Test
    void preview_leadGuestWithoutAPhone_isRejectedBeforeService() throws Exception {
        expectRejectedBeforeService(hotelBooking(
                """
                {"firstName": "Ada", "lastName": "Yılmaz", "passengerType": "ADULT", "age": 34,
                 "email": "ada@example.com"}
                """, 1, 1, 0));
    }

    @Test
    void preview_travellerWithoutAName_isRejectedBeforeService() throws Exception {
        expectRejectedBeforeService(hotelBooking(
                """
                {"firstName": "", "lastName": "", "passengerType": "ADULT", "age": 34,
                 "email": "ada@example.com", "phone": "+905551112233"}
                """, 1, 1, 0));
    }

    @Test
    void preview_invalidTravellerEmail_isRejectedBeforeService() throws Exception {
        expectRejectedBeforeService(hotelBooking(
                """
                {"firstName": "Ada", "lastName": "Yılmaz", "passengerType": "ADULT", "age": 34,
                 "email": "not-an-email"}
                """, 1, 1, 0));
    }

    @Test
    void preview_pastCheckIn_isRejectedBeforeService() throws Exception {
        String body = """
                {
                  "currency": "EUR",
                  "totalAmount": 100,
                  "leadGuestName": "Ada Lovelace",
                  "travellers": [
                    {"firstName": "Ada", "lastName": "Yılmaz", "passengerType": "ADULT", "age": 34}
                  ],
                  "hotel": {
                    "hotelName": "Grand Antalya",
                    "checkIn": "%s",
                    "checkOut": "%s",
                    "rooms": 1, "adults": 1, "children": 0,
                    "price": 100, "currency": "EUR"
                  }
                }
                """.formatted(LocalDate.now().minusDays(1), LocalDate.now().plusDays(3));

        expectRejectedBeforeService(body);
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
