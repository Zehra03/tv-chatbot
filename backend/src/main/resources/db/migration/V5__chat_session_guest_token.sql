-- V5__chat_session_guest_token.sql
-- Guest (unauthenticated) chat sessions.
--
-- The chat screen is open to guests: they can converse and browse results without an account
-- (booking still requires one). A guest is identified by an opaque, unguessable key that the
-- browser generates once and sends on every request (the X-Guest-Id header) — a bearer key, not a
-- JWT. We store it here so guest sessions get the same multi-turn continuity + history the store
-- already gives logged-in users, scoped by this key instead of user_id.
--
-- Why a separate column (not user_id): user_id is an FK to users; a guest has no user row. A guest
-- may own several sessions (the history list), so this is NOT unique — it is a per-visitor owner
-- key, mirroring how user_id groups a user's sessions.
--
-- Security: because the primary key is a sequential bigint, guest lookups are ALWAYS paired with
-- this token (findByIdAndGuestToken...), so knowing the raw id is useless without the unguessable
-- token — no IDOR across guests.

ALTER TABLE chat_sessions
    ADD COLUMN guest_token varchar(64);

COMMENT ON COLUMN chat_sessions.guest_token IS
    'Opaque per-visitor key for guest-owned sessions (from X-Guest-Id); null for user-owned sessions.';

-- A session is owned by a registered user XOR an anonymous guest (or neither, for legacy/in-memory
-- rows) — never both at once.
ALTER TABLE chat_sessions
    ADD CONSTRAINT chk_chat_sessions_single_owner
    CHECK (user_id IS NULL OR guest_token IS NULL);

-- Guest history + owner-scoped load/delete filter by guest_token, mirroring idx_chat_sessions_user_id.
CREATE INDEX idx_chat_sessions_guest_token ON chat_sessions (guest_token);
