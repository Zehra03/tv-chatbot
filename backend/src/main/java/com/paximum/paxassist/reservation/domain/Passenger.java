package com.paximum.paxassist.reservation.domain;

import java.time.OffsetDateTime;

import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import lombok.Getter;
import lombok.Setter;

/**
 * A guest/passenger on a {@link Reservation} (1:N child, cascade-deleted with the
 * parent). {@code email} and {@code phone} are PII — never log them.
 */
@Entity
@Table(name = "passengers")
@Getter
@Setter
public class Passenger {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reservation_id", nullable = false)
    private Reservation reservation;

    @Column(name = "first_name", nullable = false, length = 100)
    private String firstName;

    @Column(name = "last_name", nullable = false, length = 100)
    private String lastName;

    @Column(name = "passenger_type", nullable = false, length = 10)
    private PassengerType passengerType;

    @Column(name = "age")
    private Integer age;

    /** ISO-3166 alpha-2 country code. */
    @Column(name = "nationality", length = 2)
    private String nationality;

    /** PII. */
    @Column(name = "email", length = 254)
    private String email;

    /** PII. */
    @Column(name = "phone", length = 32)
    private String phone;

    /** DB-managed (DEFAULT now()); read back after insert, never written by the app. */
    @Generated(event = EventType.INSERT)
    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;
}
