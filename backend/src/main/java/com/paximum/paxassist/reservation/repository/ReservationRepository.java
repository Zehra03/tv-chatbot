package com.paximum.paxassist.reservation.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.paximum.paxassist.reservation.domain.Reservation;

/**
 * Persistence for reservations. Cascade on the aggregate root saves the attached {@code Passenger} /
 * hotel / flight detail entities in one call.
 */
public interface ReservationRepository extends JpaRepository<Reservation, Long> {

    /** Current user's reservations, newest first (ticket 4 list endpoint). */
    List<Reservation> findByUserIdOrderByReservationDateDescIdDesc(Long userId);

    /**
     * Public PNR + surname retrieval: the reservation whose number is {@code pnr} and which carries a
     * passenger with that surname. This is how a guest — who has no account and may be on another
     * device — gets back to their booking.
     *
     * <p>The surname is the second factor: the PNR alone must not be enough, because our numbers embed
     * the booking date ({@code PAX-yyyyMMdd-XXXXXX}) and only the six-character suffix is random.
     * It deliberately checks <em>any</em> passenger, not only the lead, so a co-traveller can look the
     * booking up with their own name as printed on it.
     *
     * <p><b>Why {@code upper} and not {@code lower}</b>: Turkish dotless ı. Postgres lower-cases
     * {@code 'YILMAZ'} to {@code 'yilmaz'} but {@code 'Yılmaz'} to {@code 'yılmaz'} — two different
     * strings, so a guest typing their surname the way it is printed on the ticket (upper case) would
     * be told their own booking does not exist. {@code upper} folds ı and i to the same {@code I},
     * collapsing all four spellings onto one value. Losing the ı/i distinction is the right trade for
     * a surname used as a lookup factor: it is meant to be forgiving about typing, not to be a
     * password.
     */
    @Query("""
            select r from Reservation r
            where r.reservationNumber = :pnr
              and exists (select 1 from Passenger p
                          where p.reservation = r and upper(p.lastName) = upper(:surname))
            """)
    Optional<Reservation> findByReservationNumberAndPassengerSurname(
            @Param("pnr") String pnr, @Param("surname") String surname);
}
