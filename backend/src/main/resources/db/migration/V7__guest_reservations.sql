-- V7__guest_reservations.sql
-- Guest (unauthenticated) reservations.
--
-- Booking no longer requires an account: a visitor can preview and confirm a reservation while
-- logged out. user_id was already nullable (V1: FK ... ON DELETE SET NULL), so nothing needs to be
-- relaxed there — what was missing is an OWNER for the rows that have no user_id.
--
-- Why guest_token (and not a bare is_guest flag): "is guest" is already derivable from
-- user_id IS NULL, so a boolean would only duplicate it. What we actually need is the same thing
-- chat_sessions solved in V5 — an opaque per-visitor key (the X-Guest-Id header) that scopes the
-- preview -> confirm handoff to the browser that started it. Without it every guest preview looks
-- like every other guest preview (owner null == null) and any visitor holding a leaked previewId
-- could confirm someone else's booking.
--
-- Retrieval for guests is by PNR (reservations.reservation_number) + surname
-- (passengers.last_name), not by this token: the token lives in localStorage and does not survive a
-- new device or a cleared browser, whereas the PNR is what the guest is actually given.

ALTER TABLE reservations
    ADD COLUMN guest_token varchar(64);

COMMENT ON COLUMN reservations.guest_token IS
    'Opaque per-visitor key for guest-owned reservations (from X-Guest-Id); null for user-owned ones.';

-- A reservation is owned by a registered user XOR an anonymous guest — never both at once.
-- (Both null stays legal: it marks a reservation whose owning user was deleted, which the V1 FK
-- ON DELETE SET NULL deliberately produces rather than dropping the booking.)
ALTER TABLE reservations
    ADD CONSTRAINT chk_reservations_single_owner
    CHECK (user_id IS NULL OR guest_token IS NULL);

CREATE INDEX idx_reservations_guest_token ON reservations (guest_token);

-- PNR + surname lookup: the PNR side is already served by uq_reservations_number, and the surname
-- is matched case-insensitively against the handful of passengers on that one reservation
-- (idx_passengers_reservation_id). This index only keeps the case-insensitive comparison from
-- forcing a sequential scan on passengers should the planner ever start from that side.
--
-- upper(), not lower(), and it must match the repository query exactly or the index is dead weight:
-- Turkish dotless ı makes lower() asymmetric — lower('YILMAZ') = 'yilmaz' but lower('Yılmaz') =
-- 'yılmaz' — so a guest typing the surname as printed on their ticket would not find their booking.
-- upper() folds ı and i onto the same I.
CREATE INDEX idx_passengers_last_name_upper ON passengers (upper(last_name));
