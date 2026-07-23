package com.paximum.paxassist.admin.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyIterable;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import com.paximum.paxassist.admin.dto.AdminReservationResponse;
import com.paximum.paxassist.auth.domain.Role;
import com.paximum.paxassist.auth.domain.User;
import com.paximum.paxassist.auth.repository.UserRepository;
import com.paximum.paxassist.reservation.domain.ProductType;
import com.paximum.paxassist.reservation.domain.Reservation;
import com.paximum.paxassist.reservation.domain.ReservationStatus;
import com.paximum.paxassist.reservation.repository.ReservationRepository;

/**
 * The admin list has to answer "whose booking is this?" for every row, across all customers. The
 * reservation entity holds only a bare {@code userId} (no JPA relation to the auth module), so the
 * owner is resolved here — and how it is resolved is the part worth testing.
 */
@ExtendWith(MockitoExtension.class)
class AdminReservationServiceTest {

    @Mock
    private ReservationRepository reservationRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private AdminReservationService service;

    private static Reservation reservation(long id, String pnr, Long userId, String guestToken) {
        Reservation r = new Reservation();
        r.setId(id);
        r.setReservationNumber(pnr);
        r.setUserId(userId);
        r.setGuestToken(guestToken);
        r.setProductType(ProductType.HOTEL);
        r.setStatus(ReservationStatus.CONFIRMED);
        r.setTotalAmount(new BigDecimal("1500.00"));
        r.setCurrency("EUR");
        r.setReservationDate(LocalDate.now());
        r.setLeadGuestName("Ada Yılmaz");
        return r;
    }

    private static User user(long id, String email, String displayName) {
        User u = User.builder()
                .email(email)
                .passwordHash("x")
                .displayName(displayName)
                .role(Role.USER)
                .build();
        u.setId(id);
        return u;
    }

    private void stubPage(List<Reservation> rows) {
        when(reservationRepository.searchForAdmin(any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(rows, PageRequest.of(0, 20), rows.size()));
    }

    @Test
    void search_attachesTheOwningAccountToMemberBookings() {
        stubPage(List.of(reservation(1L, "PAX-1", 42L, null)));
        when(userRepository.findAllById(anyIterable()))
                .thenReturn(List.of(user(42L, "ada@example.com", "Ada Yılmaz")));

        Page<AdminReservationResponse> page = service.search(null, null, null, PageRequest.of(0, 20));

        assertThat(page.getContent()).singleElement().satisfies(row -> {
            assertThat(row.guest()).isFalse();
            assertThat(row.ownerEmail()).isEqualTo("ada@example.com");
            assertThat(row.ownerName()).isEqualTo("Ada Yılmaz");
        });
    }

    @Test
    void search_guestBookingHasNoOwnerAndNeverLeaksItsToken() {
        stubPage(List.of(reservation(2L, "PAX-2", null, "secret-guest-token")));

        Page<AdminReservationResponse> page = service.search(null, null, null, PageRequest.of(0, 20));

        AdminReservationResponse row = page.getContent().get(0);
        assertThat(row.guest()).isTrue();
        assertThat(row.ownerEmail()).isNull();
        assertThat(row.ownerName()).isNull();
        // The guest token is a bearer key to the booking — it must not appear anywhere in the row.
        assertThat(row.toString()).doesNotContain("secret-guest-token");
        // No account ids to resolve, so the user table is not queried at all.
        verify(userRepository, never()).findAllById(anyIterable());
    }

    /**
     * Owners are fetched once per page, not once per row. A lookup inside the mapping loop would be
     * N+1 — invisible on the 2-row dev database and painful on a real one.
     */
    @Test
    void search_resolvesOwnersInOneBatchWithDistinctIds() {
        stubPage(List.of(
                reservation(1L, "PAX-1", 42L, null),
                reservation(2L, "PAX-2", 42L, null),   // same owner as row 1
                reservation(3L, "PAX-3", 43L, null),
                reservation(4L, "PAX-4", null, "guest-token")));
        when(userRepository.findAllById(anyIterable())).thenReturn(List.of(
                user(42L, "ada@example.com", "Ada"),
                user(43L, "mert@example.com", "Mert")));

        Page<AdminReservationResponse> page = service.search(null, null, null, PageRequest.of(0, 20));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Iterable<Long>> ids = ArgumentCaptor.forClass(Iterable.class);
        verify(userRepository).findAllById(ids.capture());
        assertThat(ids.getValue()).containsExactlyInAnyOrder(42L, 43L);

        assertThat(page.getContent()).extracting(AdminReservationResponse::ownerEmail)
                .containsExactly("ada@example.com", "ada@example.com", "mert@example.com", null);
    }

    /** A reservation whose account no longer exists must still list, just without an owner. */
    @Test
    void search_missingUserRowDoesNotDropTheReservation() {
        stubPage(List.of(reservation(5L, "PAX-5", 99L, null)));
        when(userRepository.findAllById(anyIterable())).thenReturn(List.of());

        Page<AdminReservationResponse> page = service.search(null, null, null, PageRequest.of(0, 20));

        assertThat(page.getContent()).singleElement().satisfies(row -> {
            assertThat(row.reservationNumber()).isEqualTo("PAX-5");
            assertThat(row.ownerEmail()).isNull();
            // userId is set, so this is NOT a guest booking even though the account is gone.
            assertThat(row.guest()).isFalse();
        });
    }

    @Test
    void countsByProductType_keysByLowercaseJsonForm() {
        when(reservationRepository.countByProductType()).thenReturn(List.<Object[]>of(
                new Object[] { ProductType.FLIGHT, 3L },
                new Object[] { ProductType.HOTEL, 2L }));

        assertThat(service.countsByProductType())
                .containsEntry("flight", 3L)
                .containsEntry("hotel", 2L);
    }
}
