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

## 4. CI / auto-deploy

Railway and Vercel both watch the connected GitHub branch and redeploy on push — no extra workflow
needed. Point each at the branch you release from (e.g. `main`). The existing
[`.github/workflows/ci.yml`](../.github/workflows/ci.yml) keeps running tests + build on PRs as the
gate before merge.
