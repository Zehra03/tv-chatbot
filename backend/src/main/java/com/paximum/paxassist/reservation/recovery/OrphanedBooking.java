package com.paximum.paxassist.reservation.recovery;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

import org.hibernate.annotations.Generated;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.generator.EventType;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import lombok.Getter;
import lombok.Setter;

/**
 * Durable record of an <b>orphaned TourVisio booking</b>: a CommitTransaction that succeeded (a real
 * purchase exists) but whose local {@code Reservation} persistence then failed and could not be rolled
 * back. Captures just enough to reconcile the booking manually. Standalone — no FK to reservations or
 * users (V3 migration) — so it survives even when the normal write path is broken. Holds no passenger
 * PII beyond the denormalized lead-guest name.
 */
@Entity
@Table(name = "orphaned_bookings")
@Getter
@Setter
public class OrphanedBooking {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** TourVisio's booking reference — the key to reconcile against. */
    @Column(name = "external_reservation_number", length = 64)
    private String externalReservationNumber;

    /** Our own reservation number we had generated for the failed local write. */
    @Column(name = "intended_reservation_number", length = 32)
    private String intendedReservationNumber;

    @Column(name = "transaction_id", length = 64)
    private String transactionId;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "lead_guest_name", length = 200)
    private String leadGuestName;

    @Column(name = "total_amount", precision = 12, scale = 2)
    private BigDecimal totalAmount;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "currency", length = 3)
    private String currency;

    /** Class + message of the persistence failure (no passenger PII). */
    @Column(name = "failure_reason", columnDefinition = "text")
    private String failureReason;

    @Column(name = "reconciled", nullable = false)
    private boolean reconciled = false;

    @Generated(event = EventType.INSERT)
    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;
}
