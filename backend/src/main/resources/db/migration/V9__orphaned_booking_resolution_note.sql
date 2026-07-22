-- V9__orphaned_booking_resolution_note.sql
-- Follow-up to V3 (orphaned_bookings): the table was captured on a commit-succeeded/persist-failed
-- purchase, but nothing ever read it back — no endpoint, no scheduled job ever queried it, so a
-- customer could be charged by TourVisio with no visible reservation and no one would notice short
-- of someone manually querying the table. This adds the one column the new admin reconciliation
-- endpoint needs to record HOW an entry was resolved, alongside the existing `reconciled` flag.

ALTER TABLE orphaned_bookings ADD COLUMN resolution_note text;

COMMENT ON COLUMN orphaned_bookings.resolution_note IS 'Operator note recorded when marking an orphaned booking reconciled (e.g. which Reservation id was manually created to match it).';
