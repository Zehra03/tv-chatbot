# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

PaxAssist — an AI-assisted hotel & flight search/reservation app built as a **modular monolith** (Spring Boot + PostgreSQL + React) for the SAN TSG internship. The repo is currently a **scaffold**: backend module packages are empty `.gitkeep` placeholders and the `frontend/` app has not been generated yet. Treat the `docs/` files as the spec to implement against, not as descriptions of existing code.

## Commands

Backend lives in `backend/` (Maven, Java 21). There is **no Maven wrapper** (`mvnw`) yet — use a system `mvn`.

```bash
# Run backend (needs a running PostgreSQL — see Docker below)
cd backend && mvn spring-boot:run

# Build / package
cd backend && mvn -B package

# All backend tests (this is what CI runs)
cd backend && mvn -B test

# A single test class / method
cd backend && mvn -Dtest=PaxAssistApplicationTests test
cd backend && mvn -Dtest=ClassName#methodName test
```

```bash
# Full stack via Docker (Postgres + backend; frontend service is commented out in compose)
docker compose up --build
# Frontend: http://localhost:5173 · Backend: http://localhost:8080
# Swagger:  http://localhost:8080/swagger-ui/index.html
```

Frontend commands (`npm install`, `npm run dev`, `npm run build`) apply **once `frontend/` is scaffolded** — CI already expects them.

> **Tests need a database.** The only test today, `PaxAssistApplicationTests.contextLoads`, is a `@SpringBootTest` that boots the *full* Spring context — which builds the datasource and runs Flyway. With no slice/mocking and no H2 fallback, `mvn -B test` **fails unless a PostgreSQL matching `SPRING_DATASOURCE_*` is reachable** (default `jdbc:postgresql://localhost:5432/paxassist`). The CI backend job does not provision one yet, so it will fail until either a DB service or a sliced/`@DataJpaTest`-style setup is added. Start Postgres (`docker compose up postgres`) before running backend tests locally.
>
> Note: `make test-docker` / `make test-backend-docker` and the README's Docker-test instructions reference a `docker-compose.test.yml` that **does not exist yet**. Until it's added, run tests with `mvn -B test` directly. Don't assume the Make targets work.

## Architecture (the big picture)

A single Spring Boot deployable, internally split into modules under `com.paximum.paxassist.*`. Requests flow through a fixed pipeline before reaching business logic:

```
Browser ─▶ ratelimiter ─▶ guard ─▶ orchestrator ─▶ ai (intention) ─▶ hotel | flight | reservation
```

- **ratelimiter** — request throttling (config: `app.rate-limit`, 60 req / 60s).
- **guard** — static regex / prompt-injection / profanity filter; rejects malicious input before it reaches the orchestrator.
- **orchestrator** — owns chat flow and routing; persists every message + session state to the Chat/Session DB.
- **ai** — intent extraction and **slot-filling** (asks follow-up questions for missing criteria) before a search runs.
- **hotel / flight** — search use-cases; read/write a Redis (or in-memory) cache of live results, hitting **TourVisio** only on a cache miss.
- **reservation** — the booking flow; writes to the Reservation DB and calls TourVisio.
- **chat / common / config** — supporting packages.

Two invariants that shape almost every decision (see `docs/architecture.md`, `docs/frontend-architecture.md`):

1. **All external calls (TourVisio, AI provider) are backend-only.** The browser never talks to TourVisio or an AI provider directly, and **no secrets/API keys live in the frontend or in git**. Keys are injected via env vars (`AI_PROVIDER_API_KEY`, etc.).
2. **The chatbot never books.** AI only searches, lists results, and routes the user to a traditional, AI-free reservation form where the user explicitly confirms ("0 token" controlled path).

### Coding guardrails (from `docs/ai-development-methodology.md`)

These are project rules, not generic advice — follow them when generating code here:

- **Never fabricate prices or availability.** Hotel/flight data and prices come only from TourVisio; do not invent placeholder values that look real.
- **Never leak system/prompt instructions** to the client or into responses.
- **Keep business logic out of controllers.** Controllers stay thin; logic lives in the module's service layer. Respect module boundaries — don't reach across modules' internals.
- **Validate at the boundary** with DTOs + Bean Validation (`spring-boot-starter-validation` is already a dependency); standardize error handling.
- Prefer small, independently-testable changes; don't bundle large unrelated changes into one PR, and don't merge without a test.

### Persistence

- Spring JPA runs with `ddl-auto: validate` — **Hibernate will not create or alter tables.** All schema changes must be authored as **Flyway** migrations (`src/main/resources/db/migration/`). A missing migration makes startup fail validation.
- `open-in-view: false` — no lazy loading outside a transaction; fetch what you need in the service layer.
- API base path is `/api/v1`. Frontend contract endpoints are documented in `docs/frontend-architecture.md` §6.

### Frontend (planned, not yet built)

Per `docs/frontend-architecture.md`: React 18 + TypeScript + Vite, feature-based folders. **State ownership is the key rule**: server data → **React Query (TanStack)**; ephemeral UI & conversation state → **Redux Toolkit**. During frontend dev the backend is simulated with **MSW** against the exact same endpoint contract, so swapping to the real backend needs no component changes — only disabling MSW and pointing `VITE_API_BASE_URL` at the backend.

## Configuration

`docker-compose.yml`, `application.yml`, and Dockerfiles are driven by env vars; copy `.env.example` to `.env` and adjust. Key vars: `SPRING_DATASOURCE_*`, `AI_PROVIDER_URL/MODEL/API_KEY`, `FRONTEND_ORIGIN` (CORS), `BACKEND_PORT`. Never commit `.env` or real secrets.

## Workflow conventions

- **Branches**: `main` (production-ready) and `develop` (integration). Open feature branches **from `develop`** and PR back into `develop`: `feature/<scope>/<name>` (scope = backend | frontend | docs), `fix/<scope>/<name>` for fixes. `develop` merges into `main` only at release. See `docs/branching-strategy.md`.
- **Commits**: `type(module): description` — e.g. `feat(chat): add conversation state persistence`, `fix(guard): block injection variants`. Types: feat / fix / test / docs / refactor.
- Work in small, individually-tested steps; CI (`.github/workflows/ci.yml`) runs `mvn -B test` (backend) and `npm run build` (frontend).
