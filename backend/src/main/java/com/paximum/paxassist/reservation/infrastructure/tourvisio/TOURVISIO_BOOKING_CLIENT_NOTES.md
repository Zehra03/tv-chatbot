# Ticket 2 — TourVisio Booking Client: decisions, assumptions & open questions

Reservation-module-only. Purely calls TourVisio and maps the response — no persistence,
no `Reservation` entities, no repositories, no service orchestration (that's ticket 3).
No tests in this ticket.

## HTTP client choice (with reasoning)

**Spring `RestClient` over the JDK `java.net.http.HttpClient`.**
- **RestClient, not WebClient** — the booking flow is strictly sequential/synchronous
  (BeginTransaction → … → CommitTransaction). A blocking client fits; WebClient would pull in
  `spring-webflux` and a reactive model we don't need.
- **JDK HttpClient request factory** (`JdkClientHttpRequestFactory`) — it keeps an internal
  reusable keep-alive **connection pool** and supports per-request connect/read timeouts, giving
  us "pooling + timeout policy" **with no new dependency**. Apache HttpClient5 has richer pool
  tuning but isn't on the classpath, and adding it means editing the shared `pom.xml` — outside
  this ticket's reservation-only scope. Timeouts are configurable
  (`tourvisio.booking.connect-timeout-ms` = 5000, `read-timeout-ms` = 20000; defaults, no yml change needed).

## Auth / token reuse

Reused the Flight module's existing **`TourVisioTokenProvider`** (login + token caching) by
injection — no login logic re-implemented. Credentials/base URL come from the existing
`tourvisio.*` env-driven config; nothing hardcoded.

> ⚠️ **Cross-module coupling to flag:** this makes `reservation` depend on
> `com.paximum.paxassist.flight.infrastructure.client.TourVisioTokenProvider`. The ticket asked
> to reuse rather than reimplement, so I did — but it crosses a module boundary (reservation → flight).
> **Option to consider later:** extract the TourVisio auth/token mechanism into a shared
> `common`/`tourvisio` module both can depend on. That's a change outside the reservation folder,
> so I did not do it here — let me know if you want it.

## Result mapping — four distinct outcomes (not one exception)

`TourVisioCallResult<T>` sealed interface:
- `Success<T>` — 2xx and `header.success == true`.
- `BusinessFailure<T>` — well-formed envelope with `header.success == false`; carries the full
  header so the caller branches on `messages[].code` (e.g. `"OperationCompleted"`), not just the boolean.
- `TechnicalFailure<T>` — definite failure that did NOT take effect (connection refused, 4xx,
  retries exhausted).
- `UnknownOutcome<T>` — **ambiguous** failure (timeout / network / 5xx) on a non-idempotent call;
  carries a `reservationRef` hint and the instruction to verify via `getReservationDetail`.

## Retry policy — deliberately non-uniform

| Calls | Mode | On ambiguous timeout/5xx/network |
|---|---|---|
| `beginTransaction*`, `addServices`, `removeServices`, `setReservationInfo`, `getReservationDetail`, `getReservationList`, `getCancellationPenalty` | retryable | retried up to `tourvisio.booking.max-attempts` (default 3), then `TechnicalFailure` |
| **`commitTransaction`, `cancelReservation`** | point-of-no-return | **never auto-retried** → `UnknownOutcome` (verify via `getReservationDetail`) |

- **4xx (non-401)** → `TechnicalFailure` even for commit/cancel: a rejected request never took
  effect, so it's not ambiguous.
- **401** → single forced re-login + one retry (safe even for commit/cancel: a 401 means the
  request wasn't processed). Still-401 → `TechnicalFailure`.
- **5xx / timeout / dropped connection** → ambiguous (server may have applied it).

## Security / PII

- Request & response **bodies are never logged** (setReservationInfo carries passenger PII:
  names, email, phone, passport-like fields). Only operation name, HTTP status, and non-PII
  `header.messages[].code` are logged.
- The **Bearer token / credentials are never logged**; `PiiSanitizer` provides
  `maskEmail/maskPhone/maskToken/mask` for the rare case a value must appear in a diagnostic.
- All traffic is HTTPS (the configured base URL is `https://…`).

## Fully implemented vs. best-effort

- **Fully implemented (confirmed payloads):** `getCancellationPenalty` and `cancelReservation`,
  including the nested cancel-penalty response tree (reason / services / price / priceDetail).
- **Best-effort + `// TODO: confirm real payload/response shape`:** the 7 booking-transaction
  methods. Their **request DTOs use guessed PascalCase field names** (mirroring login/pricesearch)
  and their **responses are typed only at the confirmed `header` envelope**, with `body` kept as a
  raw `JsonNode` (`RawTourVisioResponse`) so no body field names are fabricated.

## Confirmed (resolved 2026-07-07)

1. **Endpoint path casing — CONFIRMED lowercase** `/api/bookingservice/<operation>` (not PascalCase).
   Because the configured base URL (`tourvisio.url`) already ends in `/v2/api`, the code appends
   `/bookingservice/<op>` so the resolved URL is `https://…/v2/api/bookingservice/<op>` — matching the
   confirmed `/api/bookingservice/<op>` pattern. (Do NOT append `/api/bookingservice/…` or the `/api`
   would be duplicated.) Applies to both booking and cancellation endpoints.
2. **`message.id` / `message.messageType` — CONFIRMED integers** (e.g. id `10000000`, messageType `2`/`4`).
   Modelled as nullable `Integer`.
3. **`CancellationReason.id` — CONFIRMED string** (e.g. `"2"`, `"6"`), echoed back verbatim as the
   `cancelreservation` `reason` field. Modelled as `String`.
4. **`CancellationService.productType` — CONFIRMED integer** (e.g. `2`). Modelled as `Integer`.
5. **`CancellationService.relatedServices` — element shape still unknown** (always empty in available
   samples). Typed as `List<Object>` on purpose; do not guess a structure until a non-empty sample exists.

## Booking-transaction shapes — now mostly confirmed (resolved 2026-07-07)

**Finalized against real payloads:**
- **`beginTransactionWithOffer`** — request `{ offerIds[], currency, culture }` (camelCase);
  response `TransactionResponse` (`body{ transactionId, expiresOn, reservationData, status, transactionType }`).
- **`addServices`** — request `{ transactionId, offers[{ offerId, travellers[] }], currency, culture }`;
  response `TransactionResponse`.
- **`setReservationInfo`** — full typed traveller/customer tree (carries PII — never logged);
  response `TransactionResponse`. **Warning handling:** the response header may include a
  `messageType == 4` warning (e.g. `DuplicateReservationFound`) even when `success == true`. The
  header exposes `requiresConfirmation()` for this — callers must surface it for user confirmation,
  NOT treat it as plain success.
- **`commitTransaction`** — request `{ transactionId, PaymentInformation{...} }` (note the mixed
  casing: camelCase `transactionId`, PascalCase `PaymentInformation` block). PaymentInformation is a
  fixed dummy — no real payment occurs. Response `CommitTransactionResponse`
  (`body{ reservationNumber, encryptedReservationNumber, transactionId }`).

**`reservationData` internal shape** (travellers[]/reservationInfo{}/services[]/paymentDetail{})
kept as raw `JsonNode` — nested schema not documented yet.

## Still best-effort — awaiting the remaining payloads

- **`beginTransactionWithReservation`** — "with existing reservation" request body not provided
  (still a guess); response assumed same `TransactionResponse` envelope.
- **`removeServices`** — request body not provided (guess); response assumed `TransactionResponse`
  (by symmetry with addServices).
- **`getReservationDetail`** — request/response still unknown (`RawTourVisioResponse`); this is the
  call used to verify after an `UnknownOutcome`, so its response shape matters.
- **`getReservationList`** — request filters + response still unknown (`RawTourVisioResponse`).

## ⚠️ Schema gap for ticket 3 (flagging, not acting — out of scope here)

`commitTransaction` returns `reservationNumber` (e.g. `"RC002576"`) which must be persisted as
**`external_reservation_number`**. The current schema (`V1__initial_schema.sql`) only has
`reservations.reservation_number` (our own code) — there is **no `external_reservation_number`
column**. Ticket 3 (or a migration) will need to add it. I did not touch the migration or entities
(out of this ticket's scope).

## STOP — awaiting confirmation before finalizing the 7 booking methods

Per the ticket, the booking-transaction request/response shapes are unknown and implemented
best-effort. **I need the real TourVisio docs / sample payloads for BeginTransaction, AddServices,
RemoveServices, SetReservationInfo, CommitTransaction, GetReservationDetail, GetReservationList**
before turning `RawTourVisioResponse` + the guessed request DTOs into finalized typed shapes.
If those responses turn out NOT to share the `header/success/messages[]` envelope, I'll flag it
rather than force-fit them.
