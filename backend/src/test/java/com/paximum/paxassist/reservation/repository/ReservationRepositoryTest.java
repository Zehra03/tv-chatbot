package com.paximum.paxassist.reservation.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;

import jakarta.persistence.PersistenceException;

import com.paximum.paxassist.auth.domain.Role;
import com.paximum.paxassist.auth.domain.User;
import com.paximum.paxassist.reservation.domain.Passenger;
import com.paximum.paxassist.reservation.domain.PassengerType;
import com.paximum.paxassist.reservation.domain.ProductType;
import com.paximum.paxassist.reservation.domain.Reservation;
import com.paximum.paxassist.reservation.domain.ReservationStatus;

@DataJpaTest
// Use the real (Postgres) datasource + Flyway-built schema instead of an embedded H2, so the
// migrations' Postgres-specific DDL runs and Hibernate validate checks against the real schema.
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class ReservationRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private ReservationRepository reservationRepository;

    @Test
    void contextLoads_schemaIsValid() {
        // If the context loads successfully with @DataJpaTest, it means Hibernate
        // has successfully validated the entity mappings against the ResDB schema (via application-test.yml properties).
        assertThat(reservationRepository).isNotNull();
    }

    @Test
    void saveAndFind_persistsEntityCorrectly() {
        // Given — seed an owner first; reservations.user_id has a real FK to users(id).
        User owner = entityManager.persistAndFlush(User.builder()
                .email("res-repo-test@example.com")
                .passwordHash("x")
                .displayName("Res Repo Test")
                .role(Role.USER)
                .build());

        Reservation reservation = new Reservation();
        reservation.setUserId(owner.getId());
        reservation.setReservationNumber("RES-12345");
        reservation.setProductType(ProductType.HOTEL);
        reservation.setStatus(ReservationStatus.CONFIRMED);
        reservation.setTotalAmount(new BigDecimal("1500.00"));
        reservation.setCurrency("EUR");
        reservation.setReservationDate(LocalDate.now());
        reservation.setLeadGuestName("John Doe");

        // When
        Reservation saved = entityManager.persistAndFlush(reservation);
        entityManager.clear(); // Clear L1 cache to force DB read

        // Then
        Reservation found = reservationRepository.findById(saved.getId()).orElse(null);
        assertThat(found).isNotNull();
        assertThat(found.getReservationNumber()).isEqualTo("RES-12345");
        assertThat(found.getUserId()).isEqualTo(owner.getId());
        assertThat(found.getProductType()).isEqualTo(ProductType.HOTEL);
        assertThat(found.getStatus()).isEqualTo(ReservationStatus.CONFIRMED);
        assertThat(found.getCreatedAt()).isNotNull(); // DB generated
        assertThat(found.getUpdatedAt()).isNotNull(); // DB generated
    }

    @Test
    void saveGuestReservation_persistsWithNoUserIdAndIsFoundByPnrAndSurname() {
        // A booking made without an account: no user_id, owned by the opaque visitor key instead (V7).
        Reservation reservation = guestReservation("PAX-20260721-ABC123", "guest-token-1");
        Passenger passenger = new Passenger();
        passenger.setReservation(reservation);
        passenger.setFirstName("Ada");
        passenger.setLastName("Yılmaz");
        passenger.setPassengerType(PassengerType.ADULT);
        reservation.getPassengers().add(passenger);

        entityManager.persistAndFlush(reservation);
        entityManager.clear();

        // Typed the way it is printed on a ticket: upper case, and with a plain "I" for the dotless
        // "ı" most keyboards make awkward. Both must still find the booking (see the repository's
        // note on why the match folds with upper(), not lower()).
        Reservation found = reservationRepository
                .findByReservationNumberAndPassengerSurname("PAX-20260721-ABC123", "YILMAZ")
                .orElse(null);
        assertThat(found).isNotNull();
        assertThat(found.getUserId()).isNull();
        assertThat(found.isGuest()).isTrue();
        assertThat(found.getGuestToken()).isEqualTo("guest-token-1");

        assertThat(reservationRepository
                .findByReservationNumberAndPassengerSurname("PAX-20260721-ABC123", "yılmaz")).isPresent();

        // The surname is a real second factor, not decoration: the right PNR with the wrong name
        // must be as unhelpful as an unknown PNR.
        assertThat(reservationRepository
                .findByReservationNumberAndPassengerSurname("PAX-20260721-ABC123", "Demir")).isEmpty();
    }

    @Test
    void saveReservation_withBothAUserAndAGuestToken_isRejectedByTheDatabase() {
        // chk_reservations_single_owner (V7): a booking is owned by an account XOR a guest, never both.
        User owner = entityManager.persistAndFlush(User.builder()
                .email("res-dual-owner@example.com")
                .passwordHash("x")
                .displayName("Dual Owner")
                .role(Role.USER)
                .build());

        Reservation reservation = guestReservation("PAX-20260721-DUAL01", "guest-token-2");
        reservation.setUserId(owner.getId());

        assertThatThrownBy(() -> entityManager.persistAndFlush(reservation))
                .isInstanceOf(PersistenceException.class);
    }

    /**
     * The admin search must work with NO filters — that is the screen's default view.
     *
     * <p>Regression guard for a real failure: with an absent PNR the parameter reached PostgreSQL as
     * an untyped null, which it inferred as {@code bytea}, and {@code upper(bytea)} does not exist —
     * so the unfiltered list died with a SQL grammar error. The controller test cannot catch this
     * class of bug at all: it mocks the repository, so no SQL is ever generated.
     */
    @Test
    void searchForAdmin_withNoFilters_returnsRowsInsteadOfFailingOnAnUntypedNull() {
        entityManager.persistAndFlush(guestReservation("PAX-20260721-NOFILT", "guest-token-nf"));
        entityManager.clear();

        Page<Reservation> page = reservationRepository
                .searchForAdmin(null, null, null, PageRequest.of(0, 20));

        assertThat(page.getContent()).isNotEmpty();
    }

    @Test
    void searchForAdmin_matchesPartialPnrCaseInsensitivelyAndHonoursFilters() {
        entityManager.persistAndFlush(guestReservation("PAX-20260721-FIND01", "guest-token-f1"));
        entityManager.clear();

        // Kısmi ve küçük harfli parça da bulmalı: yönetici kodun tamamını nadiren elinde tutar.
        assertThat(reservationRepository
                .searchForAdmin("find01", null, null, PageRequest.of(0, 20))
                .getContent())
                .extracting(Reservation::getReservationNumber)
                .contains("PAX-20260721-FIND01");

        // Eşleşmeyen PNR boş dönmeli — "filtre yok" davranışına düşmemeli.
        assertThat(reservationRepository
                .searchForAdmin("NO-SUCH-PNR", null, null, PageRequest.of(0, 20))
                .getContent())
                .isEmpty();

        // Durum filtresi: kayıt CONFIRMED, dolayısıyla CANCELLED sorgusu onu getirmemeli.
        assertThat(reservationRepository
                .searchForAdmin("FIND01", ReservationStatus.CANCELLED, null, PageRequest.of(0, 20))
                .getContent())
                .isEmpty();

        // Ürün tipi filtresi: kayıt HOTEL, FLIGHT sorgusu onu getirmemeli.
        assertThat(reservationRepository
                .searchForAdmin("FIND01", null, ProductType.FLIGHT, PageRequest.of(0, 20))
                .getContent())
                .isEmpty();

        assertThat(reservationRepository
                .searchForAdmin("FIND01", ReservationStatus.CONFIRMED, ProductType.HOTEL,
                        PageRequest.of(0, 20))
                .getContent())
                .hasSize(1);
    }

    /** Dashboard kırılımı gerçekten SQL'de gruplanıyor mu — sayaç kartlarının kaynağı. */
    @Test
    void countByProductType_groupsRows() {
        entityManager.persistAndFlush(guestReservation("PAX-20260721-CNT001", "guest-token-c1"));
        entityManager.clear();

        assertThat(reservationRepository.countByProductType())
                .anySatisfy(row -> {
                    assertThat(row[0]).isInstanceOf(ProductType.class);
                    assertThat(row[1]).isInstanceOf(Long.class);
                });
    }

    private Reservation guestReservation(String reservationNumber, String guestToken) {
        Reservation reservation = new Reservation();
        reservation.setGuestToken(guestToken);
        reservation.setReservationNumber(reservationNumber);
        reservation.setProductType(ProductType.HOTEL);
        reservation.setStatus(ReservationStatus.CONFIRMED);
        reservation.setTotalAmount(new BigDecimal("1500.00"));
        reservation.setCurrency("EUR");
        reservation.setReservationDate(LocalDate.now());
        reservation.setLeadGuestName("Ada Yılmaz");
        return reservation;
    }
}
