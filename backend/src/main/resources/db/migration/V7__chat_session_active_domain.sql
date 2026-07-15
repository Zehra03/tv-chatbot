-- V7__chat_session_active_domain.sql
-- The domain (hotel vs flight) a chat session is currently working in.
--
-- Result cards are now kept per domain in the session, so a flight search no longer overwrites the
-- hotel cards a user may come back to ("otele dönelim"). Which box FILTER/SELECT act on is decided
-- by this pointer — so it has to survive a restart. Without it, a session restored from the DB can
-- see its cards but not tell whether they are hotels or flights, and FILTER/SELECT would be acting
-- on cards of the wrong domain.
--
-- Nullable on purpose: rows written before this column existed, and sessions where no search has run
-- yet, legitimately have no active domain. Those keep the previous single-list behaviour.

ALTER TABLE chat_sessions
    ADD COLUMN active_domain varchar(16);

COMMENT ON COLUMN chat_sessions.active_domain IS
    'HOTEL | FLIGHT | null — the domain the session is currently working in; selects which result-card box FILTER/SELECT read from.';

-- Only the two product domains are valid. NULL stays allowed (see above).
ALTER TABLE chat_sessions
    ADD CONSTRAINT chk_chat_sessions_active_domain
    CHECK (active_domain IS NULL OR active_domain IN ('HOTEL', 'FLIGHT'));
