package com.paximum.paxassist.reservation.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

import org.hibernate.annotations.Generated;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.generator.EventType;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

import lombok.Getter;
import lombok.Setter;

/**
 * Reservation header — the only persisted product data plus its list-screen row
 * ({@code reservations} table). Owns 0..N {@link Passenger} rows and up to one
 * {@link HotelReservationDetails} and/or {@link FlightReservationDetails} snapshot
 * (COMBINED bookings carry both).
 *
 * <p>{@code user_id} is mapped as a plain {@link Long} (no JPA relation to the Auth
 * module's {@code User}) to respect module boundaries.
 *
 * <p>TODO (service layer, not this ticket): enforce that {@link #productType}
 * matches {@link #deriveProductType()} and that a COMBINED reservation has
 * <em>both</em> hotel and flight details present. This is a service-layer rule,
 * deliberately NOT a DB trigger/constraint.
 */
@Entity
@Table(name = "reservations")
@Getter
@Setter
public class Reservation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "reservation_number", nullable = false, unique = true, length = 32)
    private String reservationNumber;

    /** FK to users.id (nullable, anonymous bookings). Plain value, no JPA relation. */
    @Column(name = "user_id")
    private Long userId;

    @Column(name = "product_type", nullable = false, length = 16)
    private ProductType productType;

    @Column(name = "status", nullable = false, length = 20)
    private ReservationStatus status = ReservationStatus.PENDING;

    @Column(name = "reservation_date", nullable = false)
    private LocalDate reservationDate;

    @Column(name = "total_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal totalAmount;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    /** Denormalized lead guest/passenger name for the list screen. */
    @Column(name = "lead_guest_name", length = 200)
    private String leadGuestName;

    /**
     * TourVisio's own booking reference (e.g. {@code "RC002576"}) returned by CommitTransaction —
     * distinct from our internal {@link #reservationNumber}. Null until a successful purchase.
     * Backed by the {@code external_reservation_number} column (V2 migration); uniqueness is enforced
     * by a partial unique index at the DB level.
     */
    @Column(name = "external_reservation_number", length = 64)
    private String externalReservationNumber;

    /** DB-managed (DEFAULT now()); read back after insert, never written by the app. */
    @Generated(event = EventType.INSERT)
    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    /** DB-managed (trigger set_updated_at); read back after insert/update. */
    @Generated(event = { EventType.INSERT, EventType.UPDATE })
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @OneToMany(mappedBy = "reservation", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Passenger> passengers = new ArrayList<>();

    @OneToOne(mappedBy = "reservation", cascade = CascadeType.ALL, orphanRemoval = true,
            fetch = FetchType.LAZY, optional = true)
    private HotelReservationDetails hotelDetails;

    @OneToOne(mappedBy = "reservation", cascade = CascadeType.ALL, orphanRemoval = true,
            fetch = FetchType.LAZY, optional = true)
    private FlightReservationDetails flightDetails;

    /**
     * Product type derived from which detail snapshots are attached, rather than
     * trusted from client input. The service should call this and reconcile it
     * with (or set) {@link #productType} before persisting.
     *
     * @return HOTEL / FLIGHT / COMBINED, or {@code null} when neither detail is
     *         present (an invalid reservation the service must reject).
     */
    public ProductType deriveProductType() {
        boolean hasHotel = hotelDetails != null;
        boolean hasFlight = flightDetails != null;
        if (hasHotel && hasFlight) {
            return ProductType.COMBINED;
        }
        if (hasHotel) {
            return ProductType.HOTEL;
        }
        if (hasFlight) {
            return ProductType.FLIGHT;
        }
        return null;
    }
}
