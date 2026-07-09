package com.paximum.paxassist.reservation.domain;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

import org.hibernate.annotations.Generated;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.generator.EventType;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

import lombok.Getter;
import lombok.Setter;

/**
 * Snapshot of the booked flight product + itinerary (1:0..1 with
 * {@link Reservation}). Shares its primary key with the parent reservation via
 * {@link MapsId} — {@code reservation_id} is both PK and FK, no own identity column.
 *
 * <p>All {@code *_time} columns are {@code timestamptz} instants; the calendar
 * date is contained within {@code departTime} (no separate depart-date column).
 * Return legs are {@code null} for {@link TripType#ONE_WAY}.
 */
@Entity
@Table(name = "flight_reservation_details")
@Getter
@Setter
public class FlightReservationDetails {

    /** Shared PK = owning reservation's id; populated by {@link MapsId}. */
    @Id
    @Column(name = "reservation_id")
    private Long reservationId;

    @MapsId
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reservation_id")
    private Reservation reservation;

    @Column(name = "origin", nullable = false, length = 100)
    private String origin;

    @Column(name = "destination", nullable = false, length = 100)
    private String destination;

    @Column(name = "airline", length = 100)
    private String airline;

    @Column(name = "trip_type", nullable = false, length = 10)
    private TripType tripType;

    @Column(name = "depart_time", nullable = false)
    private OffsetDateTime departTime;

    @Column(name = "arrive_time")
    private OffsetDateTime arriveTime;

    /** Return departure instant; {@code null} for one-way trips. */
    @Column(name = "return_depart_time")
    private OffsetDateTime returnDepartTime;

    /** Return arrival instant; {@code null} for one-way trips. */
    @Column(name = "return_arrive_time")
    private OffsetDateTime returnArriveTime;

    @Column(name = "stops", nullable = false)
    private Short stops;

    @Column(name = "baggage", length = 50)
    private String baggage;

    @Column(name = "passenger_count", nullable = false)
    private Short passengerCount;

    @Column(name = "price", nullable = false, precision = 12, scale = 2)
    private BigDecimal price;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    /** DB-managed (DEFAULT now()); read back after insert, never written by the app. */
    @Generated(event = EventType.INSERT)
    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;
}
