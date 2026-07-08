-- V2__refresh_tokens.sql
-- Persisted, rotating refresh tokens for the auth module.
--
-- The access token (JWT) stays short-lived and stateless; this table backs the long-lived
-- refresh token so the server can ROTATE it on every use and REVOKE it (real logout / session
-- kill), which a stateless JWT alone cannot do. Only a SHA-256 hash of the opaque token is
-- stored (the raw token is high-entropy and never persisted) so a DB leak can't replay sessions.

CREATE TABLE refresh_tokens (
    id         bigint      GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id    bigint      NOT NULL,
    token_hash varchar(64) NOT NULL,          -- SHA-256 hex of the opaque refresh token
    expires_at timestamptz NOT NULL,
    revoked_at timestamptz,                    -- non-null once rotated out or logged out
    created_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT fk_refresh_tokens_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT uq_refresh_tokens_hash UNIQUE (token_hash)
);

COMMENT ON TABLE  refresh_tokens            IS 'Persisted rotating refresh tokens; one active row per issued session.';
COMMENT ON COLUMN refresh_tokens.token_hash IS 'SHA-256 hex of the opaque token; the raw token is never stored.';
COMMENT ON COLUMN refresh_tokens.revoked_at IS 'Set when the token is rotated out (on refresh) or revoked (on logout).';

-- FK lookups + "revoke all active for this user" scans are keyed by user_id.
CREATE INDEX idx_refresh_tokens_user_id ON refresh_tokens (user_id);
-- token_hash's UNIQUE constraint already provides the B-tree index used by the refresh lookup.
