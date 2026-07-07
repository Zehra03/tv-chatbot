package com.paximum.paxassist.reservation.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.paximum.paxassist.reservation.domain.Reservation;

/**
 * Persistence for confirmed reservations (ticket 3). Cascade on the aggregate root saves the
 * attached {@code Passenger} / hotel / flight detail entities in one call. Kept bare — no custom
 * query methods in this ticket.
 */
public interface ReservationRepository extends JpaRepository<Reservation, Long> {
}
