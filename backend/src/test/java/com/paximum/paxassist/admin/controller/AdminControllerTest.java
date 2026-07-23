package com.paximum.paxassist.admin.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.paximum.paxassist.admin.dto.AdminReservationResponse;
import com.paximum.paxassist.admin.service.AdminReservationService;
import com.paximum.paxassist.auth.repository.UserRepository;
import com.paximum.paxassist.config.GlobalExceptionHandler;
import com.paximum.paxassist.reservation.domain.ProductType;
import com.paximum.paxassist.reservation.domain.ReservationStatus;
import com.paximum.paxassist.reservation.repository.ReservationRepository;
import com.paximum.paxassist.reservation.service.ReservationService;

/**
 * Standalone MockMvc, matching the pattern the other controller tests here use. That means the
 * security filter chain is NOT in play, so these tests deliberately cover only what the controller
 * itself decides: how query parameters become repository filters, and how rows are shaped.
 *
 * <p>The "only ROLE_ADMIN gets in" rule is declarative and lives one layer up
 * ({@code SecurityConfig}: {@code /api/v1/admin/**} -> {@code hasRole("ADMIN")}); a standalone setup
 * would answer 200 no matter who called, so asserting it here would prove nothing. It is verified
 * against the running app instead.
 */
@ExtendWith(MockitoExtension.class)
class AdminControllerTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private ReservationRepository reservationRepository;

    @Mock
    private ReservationService reservationService;

    @Mock
    private AdminReservationService adminReservationService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new AdminController(userRepository, reservationRepository,
                        reservationService, adminReservationService))
                .setControllerAdvice(new GlobalExceptionHandler())
                // Spring Boot registers this automatically; a standalone setup does not, and without
                // it the Pageable parameter cannot be resolved and every list call 500s.
                .setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver())
                .build();
    }

    private static AdminReservationResponse row(boolean guest) {
        return new AdminReservationResponse(7L, "PAX-20260714-A1B2C3", "TV-99",
                ReservationStatus.CONFIRMED, ProductType.FLIGHT, LocalDate.now(),
                new BigDecimal("1500.00"), "EUR", "Ada Yılmaz", guest,
                guest ? null : "owner@example.com", guest ? null : "Ada Yılmaz");
    }

    private void stubSinglePage(boolean guest) {
        // An explicit PageRequest, not the single-arg PageImpl: that one carries an Unpaged pageable
        // whose getOffset() throws, and Jackson hits it while writing the page envelope (500, not the
        // 200 the endpoint really returns). A real repository always hands back a paged result.
        when(adminReservationService.search(any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(row(guest)), PageRequest.of(0, 20), 1));
    }

    @Test
    void listReservations_noFilters_passesNullsSoEveryRowMatches() throws Exception {
        stubSinglePage(false);

        mockMvc.perform(get("/api/v1/admin/reservations"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].reservationNumber").value("PAX-20260714-A1B2C3"));

        verify(adminReservationService).search(isNull(), isNull(), isNull(), any(Pageable.class));
    }

    @Test
    void listReservations_pnrAndStatusFilters_reachTheRepositoryParsed() throws Exception {
        stubSinglePage(false);

        mockMvc.perform(get("/api/v1/admin/reservations")
                        .param("q", " A1B2C3 ")
                        .param("status", "cancelled")
                        .param("productType", "flight"))
                .andExpect(status().isOk());

        // Lowercase on the wire (the spelling the rest of the API uses) must still bind: request-param
        // enum binding is case-sensitive by default, which is why the controller parses these itself.
        verify(adminReservationService).search(
                org.mockito.ArgumentMatchers.eq("A1B2C3"),
                org.mockito.ArgumentMatchers.eq(ReservationStatus.CANCELLED),
                org.mockito.ArgumentMatchers.eq(ProductType.FLIGHT),
                any(Pageable.class));
    }

    @Test
    void listReservations_blankQuery_isTreatedAsNoFilterNotAnEmptyMatch() throws Exception {
        stubSinglePage(false);

        mockMvc.perform(get("/api/v1/admin/reservations").param("q", "   "))
                .andExpect(status().isOk());

        verify(adminReservationService).search(isNull(), isNull(), isNull(), any(Pageable.class));
    }

    @Test
    void listReservations_unknownStatus_is400NotAServerError() throws Exception {
        mockMvc.perform(get("/api/v1/admin/reservations").param("status", "banana"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void listReservations_guestBookingIsFlaggedAndCarriesNoOwner() throws Exception {
        stubSinglePage(true);

        mockMvc.perform(get("/api/v1/admin/reservations"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].guest").value(true))
                // Misafirin hesabı yoktur; sahip alanları boş kalmalı (misafir jetonu da hiç dönmez).
                .andExpect(jsonPath("$.content[0].ownerEmail").doesNotExist());
    }

    @Test
    void listReservations_memberBookingCarriesTheOwningAccount() throws Exception {
        stubSinglePage(false);

        mockMvc.perform(get("/api/v1/admin/reservations"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].guest").value(false))
                .andExpect(jsonPath("$.content[0].ownerEmail").value("owner@example.com"));
    }

    @Test
    void dashboardStats_reportsProductTypeCountsUnderLowercaseKeys() throws Exception {
        when(userRepository.count()).thenReturn(12L);
        when(reservationRepository.count()).thenReturn(5L);
        // Explicit type witness: List.of(new Object[]{...}) would spread the array into varargs and
        // hand back a List<Object> of two loose values instead of one row.
        when(reservationRepository.sumRevenueByCurrency(ReservationStatus.CONFIRMED))
                .thenReturn(List.<Object[]>of(new Object[] { "EUR", new BigDecimal("1500.00") }));
        when(adminReservationService.countsByProductType())
                .thenReturn(java.util.Map.of("flight", 3L, "hotel", 2L));

        mockMvc.perform(get("/api/v1/admin/dashboard/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalReservations").value(5))
                .andExpect(jsonPath("$.activeUsers").value(12))
                .andExpect(jsonPath("$.totalRevenueByCurrency.EUR").value(1500.00))
                // Keys match the productType spelling the frontend already uses everywhere.
                .andExpect(jsonPath("$.reservationsByProductType.flight").value(3))
                .andExpect(jsonPath("$.reservationsByProductType.hotel").value(2));
    }

    @Test
    void listReservations_honoursRequestedPageSize() throws Exception {
        stubSinglePage(false);

        mockMvc.perform(get("/api/v1/admin/reservations").param("page", "2").param("size", "25"))
                .andExpect(status().isOk());

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(adminReservationService).search(isNull(), isNull(), isNull(), pageable.capture());
        assertThat(pageable.getValue().getPageNumber()).isEqualTo(2);
        assertThat(pageable.getValue().getPageSize()).isEqualTo(25);
    }
}
