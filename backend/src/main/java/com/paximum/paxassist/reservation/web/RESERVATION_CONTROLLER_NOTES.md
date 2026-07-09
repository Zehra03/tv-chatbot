# Ticket 4 — Controller & DTOs: decisions & flags

Reservation-module-only. No Bean Validation, no global exception handler, no real ownership/current-user
check (all ticket 5). No tests.

## Endpoints (exactly the five specified)

| Method & path | Service call | Response |
|---|---|---|
| POST `/api/v1/reservations/preview` | `previewReservation` | `PreviewResponse` (previewId + summary) |
| POST `/api/v1/reservations` | `confirmReservation` / `confirmReservationAfterWarning` | created summary / needs-confirmation / outcome |
| GET `/api/v1/reservations` | `listReservations(userId)` | `List<ReservationSummaryResponse>` |
| GET `/api/v1/reservations/{id}` | `getReservationDetail(id)` | `ReservationDetailResponse` incl. `cancellationOptions` |
| PATCH `/api/v1/reservations/{id}/cancel` | `cancelReservation(id, reason, serviceIds)` | outcome |

Request/response bodies are dedicated DTOs — no JPA entity is exposed. (Exception: the large preview
request body is accepted as the `PreviewReservationCommand` service DTO — not an entity — to avoid
duplicating the whole traveller/customer/hotel/flight tree; ticket 5 can front it with a validated web
DTO if desired.)

## Things I decided / had to add (please review)

1. **Service methods added here.** Ticket 3's `ReservationService` only had preview/confirm. To wire the
   list/detail/cancel endpoints without putting business logic in the controller, I added
   `listReservations`, `getReservation`, `getReservationDetail` (with the TourVisio cancellation-penalty
   call), and `cancelReservation` to the service, plus `findByUserIdOrderByReservationDateDescIdDesc` on
   the repository. Flag if you'd rather these had lived in a separate query/cancel service.

2. **Second confirm has no dedicated endpoint** (the ticket fixed 5 endpoints). So POST
   `/api/v1/reservations` accepts an optional `confirmationToken` in the body: present → resume via
   `confirmReservationAfterWarning`; absent → `confirmReservation(previewId)`. Keeps the endpoint count
   fixed. Confirm this is how you want the second confirm exposed.

3. **`X-User-Id` header** is the unvalidated current-user placeholder on every endpoint (ticket 5 wires
   real Auth). For preview it is injected into the command via `withUserId` (falls back to the body's
   userId if the header is absent).

4. **Cancellation options on every GET {id}** — implemented per the contract (field always present,
   populated from TourVisio when an external reference exists; empty otherwise). **Flagged optimization**
   (not applied, to avoid silently changing the contract): only fetch when the status still allows
   cancellation, and/or move the HTTP call outside the read transaction (it currently runs inside it).

5. **Inline result → HTTP mapping.** The controller switches over the sealed `ConfirmationResult` /
   `CancelResult` and returns an appropriate status + a minimal `OutcomeResponse`. This is plumbing so
   each endpoint returns something meaningful; the standardized error contract / global handler is ticket 5.

## ⚠️ Known divergence from the current frontend contract (accepted — frontend adapts later)

The backend follows the ticket-mandated **stateful preview→confirm** model (Redis `previewId`, rich
TourVisio command, trusted price). The current frontend (`frontend/src/api/reservationApi.ts`, marked
provisional — "netleşince güncellenebilir") uses an older **stateless** model. These do NOT line up; the
frontend must be updated to this contract. Concrete gaps to fix on the frontend side:

- **Create**: frontend `create(body)` sends the full form body with no `previewId`; this API expects
  `POST /reservations` with `{ previewId }` (or `{ confirmationToken }`). Frontend must first call
  `/preview`, then confirm with the returned `previewId`.
- **Preview/create body**: frontend sends `{ productType, productId, passengers, currency }` and expects
  the backend to compute the total; this API expects the richer `PreviewReservationCommand`
  (offerIds/travellers/hotel/flight/customer + a supplied `totalAmount`). Price is trusted here (accepted
  risk, see ticket 3), not computed.
- **Cancel**: frontend PATCHes with no body; this API expects `CancelRequest { reason, serviceIds }`.
- **Field naming**: frontend `passengers` vs this API's `travellers`.

Decision (owner-approved): commit the backend as the source of truth; realign the frontend contract in a
later frontend task. Recorded here so the gap isn't silently lost.

## ⚠️ Needs your input — TourVisio cancel status mapping

`cancelReservation` maps TourVisio's integer `body.reservationStatus` onto our `ReservationStatus`.
The concrete TourVisio status codes aren't documented here, so `mapReservationStatus` currently
**defaults to `CANCELLED` on any successful cancel** (a successful cancel logically means cancelled),
with a TODO. Please provide the real integer→enum mapping (e.g. codes for full vs partial cancellation,
or a "pending"/"refused" state) and I'll implement it.
