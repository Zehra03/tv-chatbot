package com.paximum.paxassist.reservation.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.paximum.paxassist.reservation.domain.Reservation;

/**
 * Persistence for reservations. Cascade on the aggregate root saves the attached {@code Passenger} /
 * hotel / flight detail entities in one call.
 */
public interface ReservationRepository extends JpaRepository<Reservation, Long> {

    /** Current user's reservations, newest first (ticket 4 list endpoint). */
    List<Reservation> findByUserIdOrderByReservationDateDescIdDesc(Long userId);
}
