-- V2__add_external_reservation_number.sql
-- Ticket 3: persist TourVisio's own booking reference (returned by CommitTransaction, e.g. "RC002576")
-- alongside our human-readable reservation_number. The two are distinct identifiers:
--   reservation_number          -> our internal/UI code (PAX-yyyymmdd-...)
--   external_reservation_number -> TourVisio's booking reference (external system)
-- Nullable: only set after a successful commit; a pending/failed reservation has none.

ALTER TABLE reservations
    ADD COLUMN external_reservation_number varchar(64);

COMMENT ON COLUMN reservations.external_reservation_number IS
    'TourVisio booking reference (e.g. RC002576) from CommitTransaction; distinct from reservation_number. Null until a successful purchase.';

-- Partial unique index: a given TourVisio booking maps to at most one row, but many rows may have
-- no external number yet (multiple NULLs are allowed by a partial index).
CREATE UNIQUE INDEX uq_reservations_external_number
    ON reservations (external_reservation_number)
    WHERE external_reservation_number IS NOT NULL;
