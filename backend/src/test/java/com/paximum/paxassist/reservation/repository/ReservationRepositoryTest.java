package com.paximum.paxassist.reservation.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;

import com.paximum.paxassist.auth.domain.Role;
import com.paximum.paxassist.auth.domain.User;
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
}
