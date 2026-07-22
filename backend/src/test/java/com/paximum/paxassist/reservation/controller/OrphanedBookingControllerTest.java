package com.paximum.paxassist.reservation.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

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
import com.paximum.paxassist.reservation.recovery.OrphanedBooking;
import com.paximum.paxassist.reservation.recovery.OrphanedBookingService;

/**
 * HTTP-shape tests for the admin-only orphaned-booking recovery surface. Authorization itself
 * ({@code @PreAuthorize("hasRole('ADMIN')")} on the controller) is method-security, enforced by an
 * interceptor this standalone MockMvc setup does not register — these tests pin the contract once
 * past that gate.
 */
@ExtendWith(MockitoExtension.class)
class OrphanedBookingControllerTest {

    @Mock
    private OrphanedBookingService orphanedBookingService;

    private MockMvc mockMvc;
    private UserPrincipal mockPrincipal;

    @BeforeEach
    void setUp() {
        mockPrincipal = org.mockito.Mockito.mock(UserPrincipal.class);
        org.mockito.Mockito.lenient().when(mockPrincipal.getId()).thenReturn(999L);

        HandlerMethodArgumentResolver principalResolver = new HandlerMethodArgumentResolver() {
            @Override
            public boolean supportsParameter(MethodParameter parameter) {
                return parameter.getParameterAnnotation(AuthenticationPrincipal.class) != null;
            }
            @Override
            public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
                    NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
                return mockPrincipal;
            }
        };

        mockMvc = MockMvcBuilders.standaloneSetup(new OrphanedBookingController(orphanedBookingService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(principalResolver)
                .build();
    }

    private OrphanedBooking booking(Long id) {
        OrphanedBooking booking = new OrphanedBooking();
        booking.setId(id);
        booking.setExternalReservationNumber("RC00" + id);
        booking.setUserId(42L);
        booking.setLeadGuestName("Zehra Yılmaz");
        booking.setTotalAmount(new BigDecimal("2500.00"));
        booking.setCurrency("EUR");
        booking.setReconciled(false);
        return booking;
    }

    @Test
    void list_defaultsToUnreconciledOnly() throws Exception {
        when(orphanedBookingService.listUnreconciled()).thenReturn(List.of(booking(1L)));

        mockMvc.perform(get("/api/v1/reservations/orphaned"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].externalReservationNumber").value("RC001"))
                .andExpect(jsonPath("$[0].reconciled").value(false));

        verify(orphanedBookingService).listUnreconciled();
    }

    @Test
    void list_includeReconciledTrueListsEverything() throws Exception {
        when(orphanedBookingService.listAll()).thenReturn(List.of(booking(2L)));

        mockMvc.perform(get("/api/v1/reservations/orphaned").param("includeReconciled", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(2));

        verify(orphanedBookingService).listAll();
    }

    @Test
    void reconcile_marksTheBookingAndReturnsIt() throws Exception {
        OrphanedBooking reconciled = booking(5L);
        reconciled.setReconciled(true);
        reconciled.setResolutionNote("Reservation #77 created manually.");
        when(orphanedBookingService.reconcile(eq(5L), eq("Reservation #77 created manually."), eq(999L)))
                .thenReturn(Optional.of(reconciled));

        mockMvc.perform(patch("/api/v1/reservations/orphaned/5/reconcile")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"note\":\"Reservation #77 created manually.\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reconciled").value(true))
                .andExpect(jsonPath("$.resolutionNote").value("Reservation #77 created manually."));
    }

    @Test
    void reconcile_withoutABodyStillWorks() throws Exception {
        when(orphanedBookingService.reconcile(eq(6L), eq((String) null), eq(999L)))
                .thenReturn(Optional.of(booking(6L)));

        mockMvc.perform(patch("/api/v1/reservations/orphaned/6/reconcile"))
                .andExpect(status().isOk());
    }

    @Test
    void reconcile_unknownIdIs404() throws Exception {
        when(orphanedBookingService.reconcile(eq(404L), any(), eq(999L))).thenReturn(Optional.empty());

        mockMvc.perform(patch("/api/v1/reservations/orphaned/404/reconcile")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isNotFound());
    }
}
