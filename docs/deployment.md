# Deployment — Managed (Railway + Vercel)

Production topology for PaxAssist using managed services. No self-managed server, no GPU.

```
Vercel (frontend, static Vite build on CDN)
        │  HTTPS  (VITE_API_BASE_URL → backend)
        ▼
Railway (backend Docker + Postgres plugin + Redis plugin)
        │
        ▼  outbound only
Gemini · DeepSeek · TourVisio
```

**Ollama is NOT deployed in production.** Main chat runs on Gemini and the validator on DeepSeek's
cloud API, so no local model / GPU is needed. Leave `VALIDATOR_PROVIDER=deepseek` (the default).

---

## 1. Backend → Railway

1. New project → **Deploy from GitHub repo** → set **Root Directory = `backend`** (Railway
   auto-detects [`backend/Dockerfile`](../backend/Dockerfile)).
2. In the same project, add plugins: **PostgreSQL** and **Redis** (one click each — managed,
   backed up, health-checked).
3. On the backend service, set the environment variables below. For the DB/Redis ones, use
   Railway **variable references** so you never copy secrets by hand.

### Backend environment variables

| Variable | Value on Railway |
|---|---|
| `SPRING_PROFILES_ACTIVE` | `prod` |
| *(port)* | **nothing to set** — the app reads Railway's injected `PORT` automatically |
| `SPRING_DATASOURCE_URL` | `jdbc:postgresql://${{Postgres.PGHOST}}:${{Postgres.PGPORT}}/${{Postgres.PGDATABASE}}` |
| `SPRING_DATASOURCE_USERNAME` | `${{Postgres.PGUSER}}` |
| `SPRING_DATASOURCE_PASSWORD` | `${{Postgres.PGPASSWORD}}` |
| `SPRING_REDIS_HOST` | `${{Redis.REDISHOST}}` |
| `SPRING_REDIS_PORT` | `${{Redis.REDISPORT}}` |
| `SPRING_REDIS_PASSWORD` | `${{Redis.REDISPASSWORD}}` |
| `FRONTEND_ORIGIN` | your Vercel URL, e.g. `https://paxassist.vercel.app` (CORS) |
| `AUTH_JWT_SECRET` | **a fresh 32+ byte random secret** — never the repo default |
| `GEMINI_API_KEY` | real Gemini key |
| `AI_PROVIDER_MODEL` | `gemini-flash-latest` (or your chosen model) |
| `DEEPSEEK_API_KEY` | real DeepSeek key (validator) |
| `TOURVISIO_URL` | real TourVisio base, e.g. `https://…/v2` |
| `TOURVISIO_CULTURE` | e.g. `en-US` |
| `TOURVISIO_TIMEZONE` | e.g. `Europe/Istanbul` (required — flights are dropped without it) |
| `TOURVISIO_AGENCY` / `TOURVISIO_USER` / `TOURVISIO_PASSWORD` | real credentials |
| `AUTH_JWT_EXPIRATION_MINUTES` | `60` (optional; has a default) |
| `AUTH_REFRESH_EXPIRATION_DAYS` | `30` (optional; has a default) |

> **Why the `jdbc:` prefix?** Railway's Postgres plugin exposes a `DATABASE_URL` in
> `postgres://…` form, which Spring's JDBC datasource cannot parse. Building
> `SPRING_DATASOURCE_URL` from the individual `PG*` references (as above) is the clean, no-code
> path — that's why the app reads host/user/password separately.

Generate the JWT secret locally:
```bash
openssl rand -base64 48
```

---

## 2. Frontend → Vercel

1. New project → import the repo → **Root Directory = `frontend`**.
2. Framework preset **Vite** (auto). Build `npm run build`, output `dist` (auto-detected).
3. Environment variable:

| Variable | Value |
|---|---|
| `VITE_API_BASE_URL` | the Railway backend URL, e.g. `https://paxassist-backend.up.railway.app` |

> `VITE_*` vars are **baked in at build time** — changing this requires a redeploy.
> MSW mocks are automatically off in a production build (guarded by `import.meta.env.DEV` in
> [`src/main.tsx`](../frontend/src/main.tsx)), so no extra flag is needed.

4. After the first deploy, copy the Vercel domain into Railway's `FRONTEND_ORIGIN` and redeploy the
   backend so CORS allows it.

---

## 3. Verify (go-live checklist)

- [ ] `https://<backend>/actuator/health` returns `{"status":"UP"}` (datasource + Flyway OK)
- [ ] Frontend loads; browser Network tab shows requests hitting the Railway domain, not MSW
- [ ] A chat message returns a real reply (Gemini reachable)
- [ ] A hotel/flight search returns cards (TourVisio credentials correct)
- [ ] Login / refresh works (JWT secret set, Postgres reachable)
- [ ] `AUTH_JWT_SECRET` is a fresh value, **not** `dev-only-insecure-secret-change-me-please-32bytes+`
- [ ] `.env` is not committed (`git ls-files | grep .env` → empty; already gitignored)

## 4. Known security limitations (accepted, 2026-07-22)

Recorded here so they are decisions, not surprises. Both are known and deliberately deferred —
revisit before this is used by anyone outside the team.

**Password reset is unauthenticated.** `POST /api/v1/auth/reset-password` is `permitAll` and takes
only `{email, password}`: no token, no e-mail link, no current password. Anyone who knows a
registered e-mail address can set a new one and take over that account — and then read the
passenger names, e-mails and phone numbers on that user's reservations, or cancel them (which calls
TourVisio, with real penalties). The endpoint also returns 404 `EMAIL_NOT_FOUND`, so registered
addresses can be enumerated. Rate limiting does not help: the attack is a single request.

Why it is like this: there is no SMTP in the project, and a safe self-service reset needs a channel
only the account owner controls. The real fix is either an e-mail provider (a free tier is enough at
this volume) or removing the endpoint until there is one.

**API docs are public in production.** `/swagger-ui/**` and `/v3/api-docs/**` are `permitAll` and
SpringDoc is not disabled under the `prod` profile, so the full endpoint and schema inventory —
including the reset endpoint above — is browsable, with a live "Try it out" console. This is not a
vulnerability by itself, but it removes the reconnaissance cost of finding the one above. Disabling
it is two properties (`springdoc.api-docs.enabled=false`, `springdoc.swagger-ui.enabled=false`).

## 5. Logs

The app writes everything to **stdout** and nothing to the database — there is no log table and no
log service (see [architecture.md](architecture.md)). Under `SPRING_PROFILES_ACTIVE=prod` each line
is a JSON object (Elastic Common Schema), so Railway's log viewer can filter by field rather than by
substring. Useful fields:

| Field | Use it to |
|---|---|
| `requestId` | pull every line belonging to one request (also returned to the browser as the `X-Request-Id` response header, so a user can quote it) |
| `userId` / `guestId` | follow one visitor across chat, search and booking |
| `activity.module` / `activity.action` | filter to one operation, e.g. `confirmReservation` |
| `activity.status` | `SUCCESS` / `FAILED` / `BLOCKED` / `INVALID_LOCATION` … |

**Retention — decide this before you need it.** Railway keeps logs for a limited window that depends
on the plan (on the free/hobby tiers it is days, not months), and it is *not* configurable from this
repo. That window is the real limit on how far back an incident can be investigated, so:

- [ ] Check the current retention on the project's Railway plan and write the number here: `____`
- [ ] If it is shorter than the period you need to answer "what happened to this booking?", forward
      the stream to an external collector. Because the output is already ECS JSON, any standard
      collector ingests it without a parser — this is a Railway-side log drain, not a code change.

Nothing here is a substitute for the database: a reservation is a row in `reservations`, and that row
does not expire when the logs do.

## 6. CI / auto-deploy

Railway and Vercel both watch the connected GitHub branch and redeploy on push — no extra workflow
needed. Point each at the branch you release from (e.g. `main`). The existing
[`.github/workflows/ci.yml`](../.github/workflows/ci.yml) keeps running tests + build on PRs as the
gate before merge.
