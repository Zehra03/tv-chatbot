# Reservation module — JPA entity mapping notes & discrepancies

Scope: JPA entities + enums + converters for the reservation module only, mapped
against the **already-migrated** `V1__initial_schema.sql` (do not modify the SQL).
No service, no repository query methods, no DTOs, no tests in this ticket.

## What was built

| File | Purpose |
|------|---------|
| `domain/ReservationStatus`, `ProductType`, `PassengerType`, `TripType` | Uppercase Java enums |
| `domain/converter/*Converter` | `AttributeConverter`s (uppercase Java ⇄ lowercase/snake_case DB), `autoApply = true` |
| `domain/Reservation` | `reservations` header, IDENTITY id, 1:N passengers, 1:0..1 hotel/flight details |
| `domain/Passenger` | `passengers`, `@ManyToOne` to Reservation |
| `domain/HotelReservationDetails`, `FlightReservationDetails` | shared-PK `@OneToOne` + `@MapsId` |

`spring.jpa.hibernate.ddl-auto=validate` was **already** set in `application.yml` — left unchanged.

## Discrepancies / decisions worth flagging

1. **`product_type` CHECK already includes `combined`.** The migration's
   `ck_reservations_product_type CHECK (product_type IN ('hotel','flight','combined'))`
   already supports package bookings, so `ProductType.COMBINED` maps cleanly. No SQL change needed.

2. **`logging.app_logs`, `users`, `chat_sessions`, `chat_messages` excluded** — out of the
   reservation module's scope. `reservations.user_id` is mapped as a plain `Long userId`
   (no JPA relation to the Auth module's `User`), so the FK to `users` is honoured at the
   DB layer only.

3. **Enums stored as text via converters, not `EnumType.STRING`.** DB values are lowercase
   (`pending`, `hotel`, `adult`) / snake_case (`one_way`, `round_trip`); Java enums are
   uppercase. Converters use `name().toLowerCase()` / `valueOf(...toUpperCase())`. This is
   the only reason `TripType.ONE_WAY ⇄ one_way` round-trips correctly.

4. **`TripType` is duplicated** in the reservation module. `com.paximum.paxassist.flight.domain.TripType`
   already exists, but the reservation snapshot must not depend on the flight module's
   internals (module-boundary rule), so a local copy was created. Flag if you'd instead
   prefer a shared `common` enum — that would be a cross-module change, out of scope here.

5. **`combined` requires both details — NOT enforced here.** Per the ticket this is a
   service-layer rule (not a DB trigger). Left as a TODO on `Reservation`; `deriveProductType()`
   returns the correct type from which snapshots are attached (and `null` when neither is
   present, which the service must reject). The stored `productType` column is set by the
   service, not this entity layer.

6. **DB-managed timestamps.** `created_at` (all tables, `DEFAULT now()`) and
   `updated_at` (`reservations`, maintained by the `set_updated_at` trigger) are mapped
   with Hibernate `@Generated` so the app never writes them and reads the DB-generated
   values back after insert/update.

## Type-mapping choices needed to pass `ddl-auto=validate`

Hibernate's schema validator compares JDBC type codes, so a few columns needed explicit
mapping rather than the naive default:

- **`char(3)` currency columns** (`reservations`, `hotel_/flight_reservation_details`) →
  `String` annotated `@JdbcTypeCode(SqlTypes.CHAR)`. A plain `String` maps to `VARCHAR`
  and would fail validation against Postgres `bpchar`.
- **`smallint` columns** (`stars`, `rooms`, `adults`, `children`, `stops`, `passenger_count`)
  → Java `Short`, not `Integer` (which maps to `INTEGER`/`int4` and would mismatch `int2`).
- **`integer` `passengers.age`** → `Integer`.
- **`numeric(12,2)`** → `BigDecimal(precision=12, scale=2)`.
- **`timestamptz`** → `OffsetDateTime` (canonical `TIMESTAMP_WITH_TIMEZONE` mapping).

  > ⚠️ **Please verify at runtime.** `timestamptz` ⇄ temporal-type validation depends on
  > the exact Hibernate 6.6 / pgjdbc 42.7 pair Spring Boot 3.5 pulls. `OffsetDateTime` is
  > the canonical choice and the entities compile, but validation only truly exercises when
  > the app boots against Postgres (which this ticket didn't run — no DB/tests in scope). If
  > startup fails validation on a `*_time`/`created_at`/`updated_at` column, switching those
  > fields to `Instant` is the first thing to try.

## Explicitly left out (out of scope)

- No `ReservationService`, no business-rule enforcement (only the `deriveProductType()` helper + TODO).
- No repository — entities are validated by JPA scanning regardless. A bare
  `JpaRepository<Reservation, Long>` can be added when the service ticket needs it.
- No DTOs, no Bean Validation annotations (validation belongs at the API boundary, a later ticket).
- No changes to `V1__initial_schema.sql` or any module outside `reservation/`.
