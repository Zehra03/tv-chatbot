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

    @Query("SELECT r.currency, SUM(r.totalAmount) FROM Reservation r WHERE r.status = :status GROUP BY r.currency")
    List<Object[]> sumRevenueByCurrency(@Param("status") com.paximum.paxassist.reservation.domain.ReservationStatus status);

    /**
     * Admin list: every reservation, optionally narrowed by PNR text, status and product type.
     * Each filter is skipped when its parameter is null, so one query serves the unfiltered list
     * and every combination of the three — no Specification/Criteria plumbing for three fields.
     *
     * <p>The text search covers BOTH our own {@code reservationNumber} and TourVisio's
     * {@code externalReservationNumber}: an admin chasing a booking usually has whichever code the
     * caller read to them, and which of the two it is isn't something they should have to know.
     *
     * <p>{@code upper} rather than {@code lower} for the same Turkish dotless-ı reason documented on
     * {@link #findByReservationNumberAndPassengerSurname} — PNRs are ASCII today, but folding the
     * two the same way everywhere keeps the rule one rule.
     *
     * <p><b>Why {@code cast(:pnr as string)}</b>: without it, an absent PNR reaches PostgreSQL as an
     * untyped null, which it infers as {@code bytea} — and {@code upper(bytea)} does not exist, so
     * the UNFILTERED list (the screen's default view) failed with a grammar error. The cast pins the
     * parameter to text so the same query serves both the filtered and unfiltered cases.
     * {@code :status} / {@code :productType} need no cast: they are compared with {@code =} against a
     * column, so the type is inferred from the column.
     */
    @Query("""
            select r from Reservation r
            where (:pnr is null
                   or upper(r.reservationNumber) like upper(concat('%', cast(:pnr as string), '%'))
                   or upper(r.externalReservationNumber) like upper(concat('%', cast(:pnr as string), '%')))
              and (:status is null or r.status = :status)
              and (:productType is null or r.productType = :productType)
            """)
    org.springframework.data.domain.Page<Reservation> searchForAdmin(
            @Param("pnr") String pnr,
            @Param("status") com.paximum.paxassist.reservation.domain.ReservationStatus status,
            @Param("productType") com.paximum.paxassist.reservation.domain.ProductType productType,
            org.springframework.data.domain.Pageable pageable);

    /** Reservation counts grouped by product type — feeds the admin dashboard's hotel/flight cards. */
    @Query("SELECT r.productType, COUNT(r) FROM Reservation r GROUP BY r.productType")
    List<Object[]> countByProductType();
}
