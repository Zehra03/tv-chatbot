-- V1__initial_schema.sql
-- PaxAssist (SAN TSG AI Chatbot) - initial persisted PostgreSQL schema.
--
-- Scope: only data persisted in PostgreSQL. TourVisio hotel/flight products and the
-- progressively slot-filled search criteria are transient (Redis / chat session JSONB);
-- only the final booked product snapshot is stored (the *_reservation_details tables).
--
-- Logical groups (single physical database):
--   Chat & Session DB : chat_sessions, chat_messages            (public schema)
--   Reservation DB    : reservations, passengers, hotel_reservation_details, flight_reservation_details (public schema)
--   Log DB            : logging.app_logs                        (separate `logging` schema, no FK to operational data)
-- See docs/database-er-diagram.md for the diagram and design rationale.

-- ============================================================
-- Schemas: operational data in public, logs isolated in `logging`
-- ============================================================
CREATE SCHEMA IF NOT EXISTS logging;
COMMENT ON SCHEMA logging IS 'Log DB: asynchronous system & error logs, isolated from operational data.';

-- ============================================================
-- Shared: trigger function to maintain updated_at on mutable rows
-- ============================================================
CREATE OR REPLACE FUNCTION set_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = now();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- ============================================================
-- users - application user with authentication credentials
-- ============================================================
CREATE TABLE users (
    id            bigint       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    email         varchar(254) NOT NULL,
    password_hash varchar(255) NOT NULL,
    display_name  varchar(150),
    role          varchar(20)  NOT NULL DEFAULT 'USER',
    created_at    timestamptz  NOT NULL DEFAULT now(),
    updated_at    timestamptz  NOT NULL DEFAULT now(),
    CONSTRAINT uq_users_email UNIQUE (email),
    CONSTRAINT ck_users_role  CHECK (role IN ('USER', 'ADMIN'))
);

COMMENT ON TABLE  users               IS 'Application user with authentication credentials.';
COMMENT ON COLUMN users.email         IS 'Login identifier (unique).';
COMMENT ON COLUMN users.password_hash IS 'Hashed password (bcrypt/argon2); never plaintext.';
COMMENT ON COLUMN users.role          IS 'Authorization role: USER or ADMIN.';

CREATE TRIGGER trg_users_updated_at BEFORE UPDATE ON users
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- ============================================================
-- chat_sessions - one chatbot conversation + accumulated search criteria
-- ============================================================
CREATE TABLE chat_sessions (
    id                   bigint      GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id              bigint,
    title                varchar(200),
    accumulated_criteria jsonb       NOT NULL DEFAULT '{}'::jsonb,
    created_at           timestamptz NOT NULL DEFAULT now(),
    updated_at           timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT fk_chat_sessions_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE SET NULL
);

COMMENT ON TABLE  chat_sessions                      IS 'One chatbot conversation; holds slot-filling state.';
COMMENT ON COLUMN chat_sessions.user_id              IS 'Owner user; nullable for anonymous use.';
COMMENT ON COLUMN chat_sessions.accumulated_criteria IS 'Progressively slot-filled search criteria (transient working state).';

CREATE TRIGGER trg_chat_sessions_updated_at BEFORE UPDATE ON chat_sessions
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- ============================================================
-- chat_messages - append-only conversation transcript
-- ============================================================
CREATE TABLE chat_messages (
    id           bigint      GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    session_id   bigint      NOT NULL,
    role         varchar(16) NOT NULL,
    content      text        NOT NULL,
    result_cards jsonb,
    created_at   timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT fk_chat_messages_session FOREIGN KEY (session_id) REFERENCES chat_sessions (id) ON DELETE CASCADE,
    CONSTRAINT ck_chat_messages_role    CHECK (role IN ('user', 'assistant', 'system'))
);

COMMENT ON TABLE  chat_messages              IS 'Append-only chat transcript, one row per turn.';
COMMENT ON COLUMN chat_messages.role         IS 'Message author: user, assistant or system.';
COMMENT ON COLUMN chat_messages.result_cards IS 'Inline hotel/flight result cards (display snapshot).';

-- ============================================================
-- reservations - reservation header (backs the reservation-list screen)
-- ============================================================
CREATE TABLE reservations (
    id                 bigint        GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    reservation_number varchar(32)   NOT NULL,
    user_id            bigint,
    product_type       varchar(16)   NOT NULL,
    status             varchar(20)   NOT NULL DEFAULT 'pending',
    reservation_date   date          NOT NULL DEFAULT CURRENT_DATE,
    total_amount       numeric(12,2) NOT NULL,
    currency           char(3)       NOT NULL,
    lead_guest_name    varchar(200),
    created_at         timestamptz   NOT NULL DEFAULT now(),
    updated_at         timestamptz   NOT NULL DEFAULT now(),
    CONSTRAINT uq_reservations_number       UNIQUE (reservation_number),
    CONSTRAINT fk_reservations_user         FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE SET NULL,
    CONSTRAINT ck_reservations_product_type CHECK (product_type IN ('hotel', 'flight', 'combined')),
    CONSTRAINT ck_reservations_status       CHECK (status IN ('pending', 'confirmed', 'cancelled', 'failed')),
    CONSTRAINT ck_reservations_total_amount CHECK (total_amount >= 0)
);

COMMENT ON TABLE  reservations                    IS 'Reservation header; the only persisted product data plus its list row.';
COMMENT ON COLUMN reservations.reservation_number IS 'Human-readable code shown in the UI (e.g. PAX-20260629-000123).';
COMMENT ON COLUMN reservations.product_type       IS 'Discriminator: which detail table holds the booked snapshot.';
COMMENT ON COLUMN reservations.lead_guest_name    IS 'Denormalized guest/passenger name for the list screen.';

CREATE TRIGGER trg_reservations_updated_at BEFORE UPDATE ON reservations
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- ============================================================
-- passengers - guest/passenger people attached to a reservation (1:N)
-- ============================================================
CREATE TABLE passengers (
    id             bigint      GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    reservation_id bigint      NOT NULL,
    first_name     varchar(100) NOT NULL,
    last_name      varchar(100) NOT NULL,
    passenger_type varchar(10) NOT NULL,
    age            integer,
    nationality    varchar(2),
    email          varchar(254),
    phone          varchar(32),
    created_at     timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT fk_passengers_reservation FOREIGN KEY (reservation_id) REFERENCES reservations (id) ON DELETE CASCADE,
    CONSTRAINT ck_passengers_type        CHECK (passenger_type IN ('adult', 'child')),
    CONSTRAINT ck_passengers_age         CHECK (age IS NULL OR age BETWEEN 0 AND 120)
);

COMMENT ON TABLE  passengers             IS 'Guests/passengers on a reservation. Contact fields are PII; never log them.';
COMMENT ON COLUMN passengers.nationality IS 'ISO-3166 alpha-2 country code.';

-- ============================================================
-- hotel_reservation_details - 1:0..1 booked hotel snapshot (shared PK)
-- ============================================================
CREATE TABLE hotel_reservation_details (
    reservation_id bigint        PRIMARY KEY,
    hotel_name     varchar(200)  NOT NULL,
    region         varchar(150),
    stars          smallint,
    board_type     varchar(50),
    check_in       date          NOT NULL,
    check_out      date          NOT NULL,
    rooms          smallint      NOT NULL DEFAULT 1,
    adults         smallint      NOT NULL,
    children       smallint      NOT NULL DEFAULT 0,
    nationality    varchar(2),
    price          numeric(12,2) NOT NULL,
    currency       char(3)       NOT NULL,
    created_at     timestamptz   NOT NULL DEFAULT now(),
    CONSTRAINT fk_hotel_details_reservation FOREIGN KEY (reservation_id) REFERENCES reservations (id) ON DELETE CASCADE,
    CONSTRAINT ck_hotel_stars     CHECK (stars IS NULL OR stars BETWEEN 1 AND 5),
    CONSTRAINT ck_hotel_checkout  CHECK (check_out > check_in),
    CONSTRAINT ck_hotel_rooms     CHECK (rooms >= 1),
    CONSTRAINT ck_hotel_adults    CHECK (adults >= 1),
    CONSTRAINT ck_hotel_children  CHECK (children >= 0),
    CONSTRAINT ck_hotel_price     CHECK (price >= 0)
);

COMMENT ON TABLE hotel_reservation_details IS 'Snapshot of the booked hotel product + stay parameters (1:0..1 with reservations).';

-- ============================================================
-- flight_reservation_details - 1:0..1 booked flight snapshot (shared PK)
-- ============================================================
CREATE TABLE flight_reservation_details (
    reservation_id     bigint        PRIMARY KEY,
    origin             varchar(100)  NOT NULL,
    destination        varchar(100)  NOT NULL,
    airline            varchar(100),
    trip_type          varchar(10)   NOT NULL,
    depart_time        timestamptz   NOT NULL,   -- outbound departure instant (calendar date is contained here)
    arrive_time        timestamptz,              -- outbound arrival instant
    return_depart_time timestamptz,              -- return departure instant (NULL for one_way)
    return_arrive_time timestamptz,              -- return arrival instant   (NULL for one_way)
    stops              smallint      NOT NULL DEFAULT 0,
    baggage            varchar(50),
    passenger_count    smallint      NOT NULL,
    price              numeric(12,2) NOT NULL,
    currency           char(3)       NOT NULL,
    created_at         timestamptz   NOT NULL DEFAULT now(),
    CONSTRAINT fk_flight_details_reservation FOREIGN KEY (reservation_id) REFERENCES reservations (id) ON DELETE CASCADE,
    CONSTRAINT ck_flight_trip_type        CHECK (trip_type IN ('one_way', 'round_trip')),
    CONSTRAINT ck_flight_arrive_order     CHECK (arrive_time IS NULL OR arrive_time >= depart_time),
    CONSTRAINT ck_flight_return_legs      CHECK (
        (trip_type = 'one_way'    AND return_depart_time IS NULL AND return_arrive_time IS NULL)
     OR (trip_type = 'round_trip' AND return_depart_time IS NOT NULL)
    ),
    CONSTRAINT ck_flight_return_order     CHECK (return_depart_time IS NULL OR return_depart_time >= depart_time),
    CONSTRAINT ck_flight_return_arrive    CHECK (
        return_arrive_time IS NULL
     OR (return_depart_time IS NOT NULL AND return_arrive_time >= return_depart_time)
    ),
    CONSTRAINT ck_flight_stops            CHECK (stops >= 0),
    CONSTRAINT ck_flight_passenger_count  CHECK (passenger_count >= 1),
    CONSTRAINT ck_flight_price            CHECK (price >= 0)
);

COMMENT ON TABLE  flight_reservation_details                    IS 'Snapshot of the booked flight product + itinerary (1:0..1 with reservations).';
COMMENT ON COLUMN flight_reservation_details.depart_time        IS 'Outbound departure instant; the calendar date is contained here (no separate depart_date column).';
COMMENT ON COLUMN flight_reservation_details.return_depart_time IS 'Return departure instant; NULL for one_way trips.';

-- ============================================================
-- logging.app_logs - asynchronous system & error logs (Log DB, separate schema, PII-free)
-- Lives in the `logging` schema and carries NO foreign key to chat_sessions:
-- logs are written asynchronously and must survive even after a session is deleted.
-- ============================================================
CREATE TABLE logging.app_logs (
    id         bigint      GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    log_level  varchar(10) NOT NULL,
    module     varchar(50),
    event_type varchar(100),
    message    text        NOT NULL,
    context    jsonb,
    session_id bigint,
    created_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT ck_app_logs_level CHECK (log_level IN ('DEBUG', 'INFO', 'WARN', 'ERROR'))
);

COMMENT ON TABLE  logging.app_logs            IS 'Asynchronous system & error logs (separate logging schema); must stay PII-free.';
COMMENT ON COLUMN logging.app_logs.session_id IS 'Loose correlation to a chat session id; intentionally NOT a foreign key (logs outlive sessions, written async).';

-- ============================================================
-- Indexes (PostgreSQL does not auto-index foreign keys)
-- ============================================================
CREATE INDEX idx_chat_sessions_user_id        ON chat_sessions (user_id);
CREATE INDEX idx_chat_messages_session_created ON chat_messages (session_id, created_at);
CREATE INDEX idx_reservations_user_id          ON reservations (user_id);
CREATE INDEX idx_reservations_status           ON reservations (status);
CREATE INDEX idx_reservations_res_date         ON reservations (reservation_date DESC);
CREATE INDEX idx_passengers_reservation_id     ON passengers (reservation_id);
CREATE INDEX idx_app_logs_level_created         ON logging.app_logs (log_level, created_at DESC);
CREATE INDEX idx_app_logs_session_id            ON logging.app_logs (session_id);
-- Unique B-tree indexes for users.email and reservations.reservation_number
-- are created automatically by their UNIQUE constraints.
-- Detail tables' PK (reservation_id) already covers their FK lookup.
