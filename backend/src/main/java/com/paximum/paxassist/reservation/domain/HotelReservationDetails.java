package com.paximum.paxassist.reservation.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
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
 * Snapshot of the booked hotel product + stay parameters (1:0..1 with
 * {@link Reservation}). Shares its primary key with the parent reservation via
 * {@link MapsId} — {@code reservation_id} is both PK and FK, no own identity column.
 */
@Entity
@Table(name = "hotel_reservation_details")
@Getter
@Setter
public class HotelReservationDetails {

    /** Shared PK = owning reservation's id; populated by {@link MapsId}. */
    @Id
    @Column(name = "reservation_id")
    private Long reservationId;

    @MapsId
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reservation_id")
    private Reservation reservation;

    @Column(name = "hotel_name", nullable = false, length = 200)
    private String hotelName;

    @Column(name = "region", length = 150)
    private String region;

    @Column(name = "stars")
    private Short stars;

    @Column(name = "board_type", length = 50)
    private String boardType;

    @Column(name = "check_in", nullable = false)
    private LocalDate checkIn;

    @Column(name = "check_out", nullable = false)
    private LocalDate checkOut;

    @Column(name = "rooms", nullable = false)
    private Short rooms;

    @Column(name = "adults", nullable = false)
    private Short adults;

    @Column(name = "children", nullable = false)
    private Short children;

    /** ISO-3166 alpha-2 country code. */
    @Column(name = "nationality", length = 2)
    private String nationality;

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
