# Ticket 3 — ReservationService (preview/confirm) decisions & flags

Reservation-module-only, except the approved `V2` migration (external_reservation_number). No tests.
No HTTP controller (ticket 4), no validation annotations (ticket 5) — service trusts already-validated input.

## The core guarantee (acceptance test #1)

**The TourVisio client is never triggered until an explicit final-confirm signal.**
`previewReservation` references neither the `TourVisioBookingClient` nor the repository — there is no
code path from it into TourVisio or the DB. `bookingClient` is only reachable from
`confirmReservation` / `confirmReservationAfterWarning`. The entire begin → add → setInfo → commit
sequence lives inside confirm, per the strict reading ("hiçbir şekilde tetiklenmediği").

## State mechanism — Redis (explicit, not @Cacheable)

`PendingReservationStore` uses a plain `StringRedisTemplate` (JSON values via a self-contained
JavaTimeModule ObjectMapper), NOT `@Cacheable`, because we need explicit write/read/atomic-claim/expire
of a mutable object.
- Keys: `reservation:preview:{previewId}`, `reservation:pending-commit:{token}` (UUIDs), namespaced
  away from the `@Cacheable` hotel/flight caches.
- TTL: `app.reservation.preview-ttl-minutes` (default 15), `app.reservation.confirm-ttl-minutes`
  (default 10). Redis enforces expiry.

## Duplicate / concurrent-purchase guard (chosen mechanism)

**Delete-then-proceed via atomic `GETDEL`** (`getAndDelete`). Confirm does: non-destructive `peek`
(for a clean expiry/ownership error without consuming a valid preview) → then atomic `claim` (GETDEL).
Because Redis is single-threaded, only ONE concurrent/duplicate confirm receives the value; every
other gets empty → `DuplicateInProgress`. This holds even under simultaneous double-click / retry /
network resend, and applies identically to the second-confirm (`AwaitingCommit`) path. A lost/failed
attempt requires a fresh preview — intentional (a consumed preview must not be silently re-runnable).

## Ownership check

`confirmReservation` verifies the requesting `userId` equals the snapshot's owner **before** claiming
(so a mismatch doesn't consume a valid preview). Flagged: full ownership enforcement is finalized in
ticket 5; the check itself lives here as required.

## setReservationInfo "needs confirmation" (messageType 4) — API shape

If `setReservationInfo` succeeds but its header carries a `messageType == 4` warning (e.g.
`DuplicateReservationFound`), the service does NOT commit. It stores an `AwaitingCommit` (with the open
`transactionId` + snapshot) under a new `confirmationToken` and returns
`ConfirmationResult.NeedsUserConfirmation(token, warnings)`. The user must explicitly call
`confirmReservationAfterWarning(token, userId)` to resume at commit. (Flag: end-to-end shape designed
here; confirm it fits ticket 4's controller.)

## Result model

`ConfirmationResult` sealed interface: `Confirmed`, `NeedsUserConfirmation`, `PreviewExpired`,
`OwnershipMismatch`, `DuplicateInProgress`, `TourVisioRejected` (business), `TourVisioUnavailable`
(technical, pre-commit — no purchase), `CommitOutcomeUnknown` (ambiguous commit — verify), and
`OrphanedBooking` (commit ok, persist failed).

## Persistence

On clean commit success: map snapshot + commit response → `Reservation` (+ `Passenger` /
`HotelReservationDetails` / `FlightReservationDetails`), writing TourVisio's `reservationNumber` into
`external_reservation_number`, our generated `PAX-yyyymmdd-XXXXXX` into `reservation_number`,
status `CONFIRMED`. Saved via `reservationRepository.save` — the single cascading save is atomic in its
own transaction (no method-level `@Transactional`, deliberately, so a save failure can't leave a
rollback-only wrapper around our orphan handling).

**On TourVisio failure (business / technical / ambiguous): nothing is written to ResDB.** Documented
decision — a reservation that was never confirmed leaves no row (only logs). If you want a recorded
"failed attempt" row, that's a follow-up.

## Critical case — commit succeeds but ResDB write fails (resolved)

Handled in `handleOrphanedBooking`, two layers:
1. **Guaranteed** highest-severity ERROR log with the TourVisio `externalReservationNumber` (recoverable
   even if the DB is fully down).
2. **Durable fallback record** in a dedicated standalone `orphaned_bookings` table (V3 migration,
   `OrphanedBooking` entity + repository) — chosen approach. No FKs so it survives a broken write path;
   holds external ref, intended reservation number, transactionId, userId, lead-guest name, amount, and
   the failure reason (no passenger PII). The fallback save is itself defensively try/caught so the same
   DB failure that caused the orphan can't mask the ERROR log.

Confirm returns `ConfirmationResult.OrphanedBooking` in this case.

## Accepted risks / assumptions (flagged)

1. **No live price/availability re-check** before freezing the preview — no such method exists in the
   Hotel/Flight modules yet. The submitted price is trusted. Clearly commented; revisit later.
2. **TourVisio numeric enum guesses** in the request mapper: `passengerType` ADULT=1/CHILD=2, traveller
   `type`=1, `status`=0. Confirm against docs.
3. **`beginTransactionWithOffer` already seats the primary offers**; `addServices` is only called for
   `additionalOffers` (avoids double-booking the primary offer). Confirm this matches TourVisio's model.
4. **`reservation_number` generation** is `PAX-yyyymmdd-XXXXXX` (random suffix); uniqueness relies on the
   DB constraint. If you need a strict sequence, that's a change.

## Migration dependency

`V2__add_external_reservation_number.sql` adds the column the entity now maps. `application.yml` has
`flyway.enabled: false`, so migrations are applied out-of-band (CI/manual) — the app will fail
`ddl-auto=validate` until V2 is applied to the target DB.
