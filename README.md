# Mini WSA

Mini WSA is a local security analytics pipeline. Clients (or the included generator) post WAF-style DLR events to **ingest-api**. Ingest validates the payload, stamps `receivedAt`, and publishes to Kafka. **analytics-service** consumes that topic, classifies the attack, scores threat, persists to PostgreSQL, and exposes query APIs.

Java 17+, Maven 3.8+, Docker Desktop, and a free local stack (PostgreSQL 16 + Kafka KRaft). No cloud databases.

## Architecture

### End-to-end runtime

```text
  generator / curl / Swagger UI
              |
              |  POST /v1/events/ingest
              v
        ingest-api :8081
        (validate → stamp receivedAt → Kafka produce)
              |  HTTP 201 = broker ack (not Postgres write)
              v
   security-events.raw  ----------->  analytics-service :8080
   security-events.dlq  <-----------  (poison / exhausted retries)
              |                         |
              |                         |  consume → classify/score → Postgres
              |                         v
              |                    PostgreSQL 16
              |                         |
              |                         |  GET summary / samples / timeseries / alerts
              |                         v
              |                    clients / Swagger UI
              |
   Infra (Compose): Postgres :5432, Kafka :9092,
                    Adminer :8082, Kafka UI :8083
   Quality (optional profile): SonarQube Community :9000
   OpenAPI YAML + Swagger UI on both apps (see below)
```

### Module ownership

| Module | Port | Owns | Does not own |
|---|---|---|---|
| `miniwsa-contracts` | — | Wire models/enums from OpenAPI (`SecurityEventMessage`, severity/action/category) | Scoring, JPA, Spring, REST query DTOs |
| `ingest-api` | 8081 | Validate + stamp `receivedAt` + Kafka produce; error schemas | Database, classify/score, alerts |
| `analytics-service` | 8080 | Kafka consume, domain enrich, Postgres, read APIs, persisted alerts, rate limit on stats/samples | Ingest HTTP, shared REST types with ingest |
| `generator` | — | CLI synthetic waves using contracts jar | Persistence, OpenAPI codegen |
| `miniwsa-coverage` | — | Aggregated JaCoCo HTML/XML report after `mvn verify` | Application code |

### Layers inside each Spring app

**ingest-api:** `api` (OpenAPI) → `controller` → `service` → Kafka `client`. No `domain/`, no `entity/`, no repository.

**analytics-service:** `api` → `controller` → `service` → `manager`/`domain` → `repository`/`entity`. MapStruct `mapper` sits between contract/api and entity. Messaging consumer/producer are adapters only.

```text
api (generated) ──> controller ──> service ──> manager/domain
                                      │
                                   mapper
                                      │
                                   entity/repository
```

### Package / folder map

```text
miniwsa-contracts/
  src/main/resources/open-api/security-event.yaml
  target/.../com.miniwsa.contracts/   # generated wire models

ingest-api/
  open-api/ingest-api.yaml
  com.miniwsa.ingest.api/             # generated Error* + hand-written EventsApi (oneOf body)
  controller / service / client / exception / config

analytics-service/
  open-api/analytics-api.yaml
  com.miniwsa.analytics.api/          # generated HTTP models + API interfaces
  controller / service / manager / domain / entity / repository / mapper / messaging / web / config

generator/
  com.miniwsa.generator/              # CLI only; uses contracts jar

miniwsa-coverage/                     # pom-only; jacoco report-aggregate
```

Dependency injection: constructor injection everywhere. Use `@RequiredArgsConstructor` for collaborator-only beans; when config is needed, put `@Value` on **constructor parameters** (not mutable fields). Controllers stay thin; shared ingest error helpers live under `exception/` (`ErrorDetails`, `ThrowableMessages`).

### Data flow notes

- The **same wire contract** is used on HTTP ingest and on the Kafka payload.
- `attackType` and `threatScore` exist only after analytics enrichment (not on the wire).
- Rate limit (100 req/IP/min) applies to **stats and samples only**; ingest and alerts are not limited.

### OpenAPI / Swagger UI

Contract-first: edit YAML under `src/main/resources/open-api/`, then `mvn generate-sources` (also runs as part of `mvn verify` / `compile`). Swagger UI loads those YAML files:

- ingest: http://localhost:8081/swagger-ui/index.html
- analytics: http://localhost:8080/swagger-ui/index.html

Use `/swagger-ui/index.html` (not bare `/swagger-ui.html`). Restart apps after OpenAPI/springdoc config changes.

## Local hub page

Open [`index.html`](index.html) in a browser (double-click from the repo root). It lists links to ingest, analytics, Swagger, Adminer, Kafka UI, and sample query URLs, and probes which ports are up.

Adminer: hub **Connect (from config)** → `http://localhost:8080/adminer-go.html`. Static JS loads `GET /adminer-go/config` (user/password/db from `SPRING_DATASOURCE_*`, server from `ADMINER_DEFAULT_SERVER`, default `miniwsa-postgres`) and POSTs into `:8082`. Do not open `http://localhost:8082/?pgsql=postgres&…`.

## Run locally (Windows)

1. Copy [`.env.example`](.env.example) to `.env`. Set `POSTGRES_PASSWORD` / `SPRING_DATASOURCE_PASSWORD`. Leave `SONAR_TOKEN` empty until you create a local Sonar token (never commit a real token).
2. Start infrastructure from the repo root (Postgres, Kafka, Adminer, Kafka UI — **not** Sonar):

```powershell
docker compose --env-file .env up -d
```

3. Build and test (JaCoCo coverage gate ≥95% LINE and BRANCH):

```powershell
mvn verify
```

HTML reports:

- Aggregate: `miniwsa-coverage/target/site/jacoco-aggregate/index.html`
- Per module: `<module>/target/site/jacoco/index.html`
- Last published numbers: [`QUALITY_REPORT.md`](QUALITY_REPORT.md)

If Maven/OpenAPI hits path encoding issues on a Hebrew folder path, copy the tree to an ASCII path (e.g. `C:\Temp\miniwsa-build`) and run `mvn verify` there.

4. Start the services (two terminals). Load the same env vars the JVM can see (`SPRING_DATASOURCE_*`, `KAFKA_BOOTSTRAP_SERVERS=localhost:9092`).

```powershell
mvn -pl ingest-api spring-boot:run
mvn -pl analytics-service spring-boot:run
```

Kafka topics `security-events.raw` and `security-events.dlq` are created on first produce/consume (auto-create is enabled). Names come from `KAFKA_TOPIC_RAW` / `KAFKA_TOPIC_DLQ`.

### Testing strategy

Prefer **MockMvc API/E2E tests** (real Spring context + H2 for analytics) to cover HTTP → service → persistence paths in one place. Keep **focused unit tests** for:

- Domain / manager scoring (`EnrichmentManager`)
- Enrichment idempotency (`EventEnrichmentService`)
- Messaging adapters not loaded under the `test` profile (`KafkaEventProducer`, `DlqPublisher`, `SecurityEventConsumer`)
- Generator CLI parsing and event factory

Avoid duplicating the same scenario in both a thin controller UT and an E2E test.

### Local quality (JaCoCo + SonarQube Community)

Everything runs on your Windows machine: free **JaCoCo** in Maven, free **SonarQube Community** in Docker. No SonarCloud, no paid license.

| Tool | Role | Gate |
|---|---|---|
| JaCoCo | Line/branch coverage; HTML + XML under `target/site/jacoco*` | `mvn verify` fails if LINE or BRANCH &lt; 95% |
| SonarQube | Code smells, bugs, vulnerabilities, security hotspots; imports JaCoCo coverage | UI review on `http://localhost:9000` (Maven does not wait on Sonar QG) |

Sonar analyzes **`src/main/java` only** (`sonar.sources`), so OpenAPI/MapStruct output under `target/generated-sources` is out of scope without a long exclusion list. JaCoCo still excludes generated class names plus a few mains/CLI/Postgres-only types (see parent `pom.xml`).

```powershell
# Coverage + gate (≥95% LINE/BRANCH)
mvn verify

# Local SonarQube (optional Compose profile — keeps daily `up -d` light)
docker compose --profile sonar up -d
# Open http://localhost:9000 — first login admin/admin, set a new password,
# create a user token, put it in .env as SONAR_TOKEN=

mvn verify sonar:sonar "-Dsonar.token=$env:SONAR_TOKEN"
```

After a Sonar run, refresh the Sonar rows in [`QUALITY_REPORT.md`](QUALITY_REPORT.md).

### Docker images

From the repo root:

```powershell
docker build -f ingest-api/Dockerfile -t miniwsa-ingest .
docker build -f analytics-service/Dockerfile -t miniwsa-analytics .
```

Host-run Java against Compose Kafka is the default path. Container networking for Kafka advertised listeners is left as a follow-up.

## Try it

Ingest a fixture (PowerShell):

```powershell
$body = @'
{
  "eventId": "evt-00132",
  "timestamp": "2026-05-20T14:32:10Z",
  "configId": 14227,
  "policyId": "pol_web1",
  "clientIp": "203.0.113.42",
  "hostname": "www.example.com",
  "path": "/api/v1/login",
  "method": "POST",
  "statusCode": 403,
  "userAgent": "Mozilla/5.0",
  "rule": {
    "id": "950001",
    "name": "SQL_INJECTION",
    "message": "SQL Injection Attack Detected",
    "severity": "CRITICAL",
    "category": "INJECTION"
  },
  "action": "DENY",
  "geoLocation": { "country": "CN", "city": "Beijing" },
  "requestSize": 1024,
  "responseSize": 256
}
'@
Invoke-RestMethod -Method Post -Uri http://localhost:8081/v1/events/ingest -ContentType application/json -Body $body
```

A **201** means Kafka accepted the message, not that PostgreSQL already stored it. Query after a short consumer lag:

```powershell
Invoke-RestMethod http://localhost:8080/v1/stats/summary?configId=14227
Invoke-RestMethod "http://localhost:8080/v1/events/samples?category=INJECTION&limit=20&offset=0"
Invoke-RestMethod "http://localhost:8080/v1/stats/timeseries?interval=5m&configId=14227"
```

Generator (`sql`/`xss` are same-IP + same-path bursts; `mixed` alternates random traffic with bursts):

```powershell
.\generator\generate.ps1 -Count 20 -Rate 5 -Wave sql -BurstSize 10
```

Alerts are stored in Postgres and survive restarts. `GET /v1/alerts/evaluate` without `alertId` checks every defined rule:

```powershell
$alert = Invoke-RestMethod -Method Post -Uri http://localhost:8080/v1/alerts/define -ContentType application/json -Body '{"configId":14227,"category":"INJECTION","metric":"event_count","window":"5m","threshold":10}'
Invoke-RestMethod http://localhost:8080/v1/alerts/evaluate
```

Stats and samples APIs are limited to **100 requests per IP per minute** (429 when exceeded). Ingest and alerts are not rate limited.

## Storage

PostgreSQL is the system of record for enriched events and alert definitions. It gives durable SQL aggregations (`GROUP BY`, `date_bin`, distinct IPs) without a paid warehouse. Kafka is the ingest buffer: it decouples HTTP accept from scoring and lets poison messages go to a DLQ. That split keeps ingest-api from owning a schema and keeps analytics as the only writer.

## Threat score

Computed only in analytics `EnrichmentManager`:

- Severity: CRITICAL 40, HIGH 30, MEDIUM 20, LOW 10
- Action: DENY 20, ALERT 10, MONITOR 0
- Path contains `/admin` or `/login` (case-insensitive): +15
- Same `clientIp` already has more than 5 events in the last 10 minutes: +15
- Cap 100 (current max of the formula is 90)

Attack type mapping: INJECTION → SQL/Command Injection, XSS → Cross-Site Scripting, PROTOCOL_VIOLATION → Protocol Anomaly, DATA_LEAKAGE → Data Exfiltration, BOT → Bot Activity, DOS → Denial of Service, RATE_LIMIT → Rate Limiting.

## Tags

| Tag | Meaning |
|---|---|
| `v0.1-ingestion` | Ingest API |
| `v0.2-enrichment` | Consumer + persist |
| `v0.3-stats` | Summary |
| `v0.4-samples` | Samples + IT |
| `v0.5-timeseries` | Timeseries |
| `v0.6-alerts` | Alerts |
| `v0.7-rate-limit` | IP rate limit |
| `v0.8-generator` | CLI generator |
| `v0.9-platform` | Docs + Dockerfiles |
| `v1.0-quality` | JaCoCo ≥95% gate + local SonarQube Community |

## What we learned

- **201 does not mean the event is in the database.** Ingest only waits for Kafka to accept the message. Analytics reads from Kafka later and writes to Postgres. That split made the ingest API smaller and easier to reason about.
- **Two services need clear ownership.** Ingest has no database. Analytics owns Postgres and the score formula. The shared `miniwsa-contracts` jar only holds the JSON shape, so changing how we score attacks does not force a change to the message type.
- **SQL is a good fit for the stats APIs.** Summary, top attackers, and time buckets are plain queries (`GROUP BY`, `AVG`, `date_bin`). We did not need a separate analytics product for this size of data.
- **Idempotency matters with Kafka.** The same `eventId` can show up twice (retry, redelivery). We skip insert when the id already exists, so duplicates do not inflate counts.
- **API tests cover more than thin unit tests for the same path.** MockMvc E2E against the real Spring stack (H2 for analytics) exercises controller → service → repository together; keep unit tests for scoring, enrichment edge cases, and Kafka adapters.

## Challenges

- **Kafka on Windows Docker.** Getting the host app to talk to Kafka inside Docker was the hardest setup step. The broker must advertise an address the host can reach (`localhost:9092`). Wrong advertised listeners meant produce/consume hung or failed even when the container was healthy.
- **Postgres-only SQL in tests.** Timeseries uses `date_bin`, which H2 does not support the same way. Production uses `PostgresTimeseriesBucketQuery`; the `test` profile uses `InMemoryTimeseriesBucketQuery`.
- **Host env vars leaking into tests.** A local `SPRING_DATASOURCE_URL` pointed tests at real Postgres while the test profile expected H2. We fixed that by setting the H2 URL on the Spring Boot test annotations.
- **Hebrew path + OpenAPI codegen.** On some Windows setups, Maven under a non-ASCII project path fails to load generated classes. Build/test from an ASCII copy (e.g. `C:\Temp\miniwsa-build`) when that happens.

## If we had more time

Platform and reliability:

- Run ingest and analytics as Compose services too, with Kafka listeners that work for containers as well as the host (Adminer and Kafka UI are already in Compose).
- Add an end-to-end test: post an event to ingest, wait for the consumer, assert a row and a summary count.
- Build a small web page that charts `/v1/stats/timeseries` and shows `/v1/stats/summary`.
- Make ingest batch publish all-or-nothing (today a mid-batch Kafka failure can leave some events already sent).
- Stronger consumer error handling around poison messages and clearer DLQ inspection in day-to-day ops.
- Move DB (and any future API) credentials out of env files into a secret manager (e.g. Vault, cloud secret store, or Kubernetes Secrets), and inject them at runtime instead of keeping passwords/keys on disk or in plain process env.

AI / LLM (on top of the current pipeline, not instead of it):

Today **classify** is a fixed map from `rule.category`, and **threatScore** is a fixed formula. Those should stay as they are: fast, cheap, and easy to test. AI would help analysts *use* the stored events, not replace those two steps.

- **Semantic search over past events.** Embed fields such as path, rule message, and user agent. When an analyst opens one event, return “similar past events” so they can see if this looks like a known campaign. A small **reranker** model can reorder the top candidates so the most useful matches rise first.
- **LLM explanations, not scores.** Keep `threatScore` from the formula. Ask an LLM to write a short plain-language note: what happened, why the score is high, and what to check next. That helps SOC users without making scores random or hard to reproduce.
- **Natural-language queries on top of SQL stats.** Let someone ask “top IPs attacking /login last hour” and turn that into calls to summary / samples / timeseries, instead of writing query params by hand.
- **Optional second opinion on edge cases.** For odd free-text (weird user agents, unusual paths) an LLM or embedding-based check could flag “looks unusual vs recent traffic” as an extra field. It would not overwrite category or threat score.
