package com.paximum.paxassist.reservation.recovery;

import org.springframework.data.jpa.repository.JpaRepository;

/** Persistence for {@link OrphanedBooking} fallback records. Bare — no custom queries in this ticket. */
public interface OrphanedBookingRepository extends JpaRepository<OrphanedBooking, Long> {
}
