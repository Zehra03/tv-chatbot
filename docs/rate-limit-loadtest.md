# Rate limiter load tests (Gatling)

Gatling 3.11 (Java DSL) simulations that exercise the rate limiter, plus a
Testcontainers integration test for the fail-open path.

- Simulations: `backend/src/test/java/com/paximum/paxassist/ratelimiter/loadtest/`
- Fail-open test: `backend/src/test/java/com/paximum/paxassist/ratelimiter/RateLimiterFailOpenIntegrationTest.java`
- Profile: `backend/src/main/resources/application-loadtest.yml`
  (`/api/search/**` capacity 10 / refill 10 per 60s, `/actuator/health` excluded,
  `fail-open: true`). The profile also excludes the JDBC/JPA/Flyway stack so the app
  boots with only Redis + web.

## Scenarios

| # | Simulation class | Asserts |
|---|------------------|---------|
| 1 | `SingleKeyBreachSimulation` | first `capacity` requests → 200, the rest → 429 |
| 2 | `IndependentKeysSimulation` | each distinct `X-Forwarded-For` gets its own limit; no cross-key 429 |
| 3 | `RefillSimulation` | exhaust, wait `retryAfterSeconds` from the 429 body, next request → 200 |
| 4 | `ExcludedPathSimulation` | `/actuator/health` never returns 429 under load |
| 5 | `RateLimiterFailOpenIntegrationTest` (JUnit, not Gatling) | Redis stopped → 200 + `ratelimit.fail_open` counter increments (asserted via MeterRegistry) |

Scenario 2 varies the key via `X-Forwarded-For` because the temporary resolver reads
that header (falling back to the socket address). This mirrors a real
proxy/load-balancer deployment and is only needed until the Security-based resolver lands.

## Running scenarios 1–4

Start Redis and the app under the `loadtest` profile, then run a simulation.

```bash
# 1) Redis
docker compose up -d redis

# 2) App with the loadtest profile (runs on :8080)
cd backend
SPRING_PROFILES_ACTIVE=loadtest mvn spring-boot:run
```

```bash
# 3) In another shell — run one simulation (non-interactive)
cd backend
mvn gatling:test -Dgatling.simulationClass=com.paximum.paxassist.ratelimiter.loadtest.SingleKeyBreachSimulation
mvn gatling:test -Dgatling.simulationClass=com.paximum.paxassist.ratelimiter.loadtest.IndependentKeysSimulation
mvn gatling:test -Dgatling.simulationClass=com.paximum.paxassist.ratelimiter.loadtest.RefillSimulation
mvn gatling:test -Dgatling.simulationClass=com.paximum.paxassist.ratelimiter.loadtest.ExcludedPathSimulation
```

Override the target or tuning via system properties, e.g.
`-DbaseUrl=http://localhost:8080 -Dcapacity=10 -Dusers=20`.

**Report:** each run writes an HTML report to `backend/target/gatling/<simulation>-<timestamp>/index.html`.
Response-time **p50/p95/p99** and the per-request 200/429 breakdown (429 ratio) are in
that report out of the box — no custom reporting is added. A failed assertion
(`global().failedRequests().count().is(0)`) fails the build, so an unexpected 429 (or a
missing one) is caught.

## Running scenario 5 (fail-open)

```bash
cd backend
mvn -Dtest=RateLimiterFailOpenIntegrationTest test
```

Needs a Testcontainers-compatible Docker environment (it starts a Redis container,
stops it mid-test, and asserts fail-open + the counter via the app's MeterRegistry).

## Known environment prerequisites

- **App boot:** the TourVisio clients read `tourvisio.*`, bound from the
  `TOURVISIO_*` environment variables (see `.env` / `docker-compose.yml`). There are
  no in-yaml defaults, so those variables must be present for the app to start, e.g.
  `TOURVISIO_URL=... TOURVISIO_AGENCY=... TOURVISIO_USER=... TOURVISIO_PASSWORD=...`.
- **Testcontainers:** scenario 5 requires a Docker daemon the Testcontainers docker-java
  client can talk to. Very new Docker Engine builds can reject the bundled client's API
  negotiation; use a compatible Docker/Testcontainers pairing (CI Linux runners work).
