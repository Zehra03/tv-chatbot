package com.paximum.paxassist.reservation.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;

import com.paximum.paxassist.reservation.domain.ProductType;
import com.paximum.paxassist.reservation.domain.Reservation;
import com.paximum.paxassist.reservation.domain.ReservationStatus;

@DataJpaTest
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
        // Given
        Reservation reservation = new Reservation();
        reservation.setUserId(123L);
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
        assertThat(found.getUserId()).isEqualTo(123L);
        assertThat(found.getProductType()).isEqualTo(ProductType.HOTEL);
        assertThat(found.getStatus()).isEqualTo(ReservationStatus.CONFIRMED);
        assertThat(found.getCreatedAt()).isNotNull(); // DB generated
        assertThat(found.getUpdatedAt()).isNotNull(); // DB generated
    }
}
