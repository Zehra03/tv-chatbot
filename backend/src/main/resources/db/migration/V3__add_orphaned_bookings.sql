-- V3__add_orphaned_bookings.sql
-- Ticket 3: durable fallback for the critical failure case where a TourVisio CommitTransaction
-- SUCCEEDS (a real purchase exists) but the subsequent ResDB persistence of the Reservation FAILS.
-- Such a booking cannot be rolled back; this table captures enough to reconcile it manually.
--
-- Deliberately standalone: NO foreign keys to reservations/users — it must survive even when the
-- normal reservation write path is broken. All business columns are nullable (best-effort capture).

CREATE TABLE orphaned_bookings (
    id                          bigint        GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    external_reservation_number varchar(64),
    intended_reservation_number varchar(32),
    transaction_id              varchar(64),
    user_id                     bigint,
    lead_guest_name             varchar(200),
    total_amount                numeric(12,2),
    currency                    char(3),
    failure_reason              text,
    reconciled                  boolean       NOT NULL DEFAULT false,
    created_at                  timestamptz   NOT NULL DEFAULT now()
);

COMMENT ON TABLE  orphaned_bookings                             IS 'TourVisio purchases that committed but failed to persist as a Reservation; for manual reconciliation. No FKs by design.';
COMMENT ON COLUMN orphaned_bookings.external_reservation_number IS 'TourVisio booking reference from CommitTransaction; the key identifier to reconcile against.';
COMMENT ON COLUMN orphaned_bookings.failure_reason             IS 'Class + message of the persistence failure (no passenger PII).';
COMMENT ON COLUMN orphaned_bookings.reconciled                 IS 'Set true once an operator has reconciled this orphaned booking.';

CREATE INDEX idx_orphaned_bookings_reconciled ON orphaned_bookings (reconciled, created_at);
