# WSA

WSA is a local security analytics pipeline. Clients (or the included generator) post WAF-style DLR events to **ingest-api**. Ingest validates the payload, stamps `receivedAt`, commits to a transactional outbox, and returns HTTP 201; a relay then publishes to Kafka. **analytics-service** consumes that topic, classifies the attack, scores threat, persists to PostgreSQL, and exposes query APIs.

Java 17+, Maven 3.8+, Docker (Desktop on Windows/macOS, or OrbStack/Colima on macOS), and a free local stack (PostgreSQL 16 + Kafka KRaft). No cloud databases. Runs on **Windows and macOS**.

## Architecture

### End-to-end runtime

```text
  generator / curl / Swagger UI
              |
              |  POST /v1/events/ingest
              v
        ingest-api :8081
        (validate → stamp receivedAt → outbox TX)
              |  HTTP 201 = durable outbox accept
              |  relay (async) → Kafka produce
              v
   security-events.raw  ----------->  analytics-service :8080
   security-events.dlq  <-----------  (poison / exhausted retries; sync DLQ wait)
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
| `contracts` | — | Wire models/enums from OpenAPI (`SecurityEventMessage`, severity/action/category) | Scoring, JPA, Spring, REST query DTOs |
| `ingest-api` | 8081 | Validate + stamp `receivedAt` + durable outbox + Kafka relay; error schemas | Enrichment DB, classify/score, alerts |
| `analytics-service` | 8080 | Kafka consume, domain enrich, Postgres, read APIs, persisted alerts, rate limit on stats/samples | Ingest HTTP, shared REST types with ingest |
| `generator` | — | CLI synthetic waves using contracts jar | Persistence, OpenAPI codegen |
| `coverage` | — | Aggregated JaCoCo HTML/XML report after `mvn verify` | Application code |

### Layers inside each Spring app

**ingest-api:** `api` (OpenAPI) → `controller` → `service` → `entity`/`repository` (outbox only) + Kafka `client` (relay). No `domain/`; no enrichment schema.

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
contracts/
  src/main/resources/open-api/security-event.yaml
  target/.../com.wsa.contracts/   # generated wire models

ingest-api/
  open-api/ingest-api.yaml
  com.wsa.ingest.api/             # generated Error* + hand-written EventsApi (oneOf body)
  controller / service / entity / repository / client / exception / config
  db/migration/                   # Flyway: ingest_outbox only

analytics-service/
  open-api/analytics-api.yaml
  com.wsa.analytics.api/          # generated HTTP models + API interfaces
  controller / service / manager / domain / entity / repository / mapper / messaging / web / config

generator/
  com.wsa.generator/              # CLI only; uses contracts jar

coverage/                     # pom-only; jacoco report-aggregate
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

Adminer: hub **Connect (from config)** → `http://localhost:8080/adminer-go.html`. Static JS loads `GET /adminer-go/config` (user/password/db from `SPRING_DATASOURCE_*`, server from `ADMINER_DEFAULT_SERVER`, default `wsa-postgres`) and POSTs into `:8082`. Do not open `http://localhost:8082/?pgsql=postgres&…`.

## One-command bootstrap (recommended)

From the repo root, these scripts install missing tools (when possible), start Compose, build, run both apps, and smoke-test until the stack answers on localhost.

```powershell
# Windows (PowerShell)
.\scripts\bootstrap-windows.ps1
```

```bash
# macOS
chmod +x scripts/*.sh generator/*.sh
./scripts/bootstrap-macos.sh
```

Useful flags:

| Windows | macOS | Meaning |
|---|---|---|
| `-SkipInstall` | `--skip-install` | Do not winget/brew install (tools already present) |
| `-FullVerify` | `--full-verify` | `mvn clean verify` (JaCoCo ≥95%) instead of `install -DskipTests` |
| `-SkipApps` | `--skip-apps` | Build + infra only (do not start Spring apps) |
| `-SkipCompose` | `--skip-compose` | Skip `docker compose up` |

**Notes**

- First Docker Desktop install may require a **Windows reboot** (hypervisor/WSL). Re-run with `-SkipInstall` after Docker says running.
- On Windows, if the checkout path is non-ASCII (e.g. Hebrew), the script syncs to `C:\Temp\wsa-build` for Maven/Compose. **Git stays in the original folder** (`Mini WSA`).
- App logs: `target/bootstrap-ingest.log` and `target/bootstrap-analytics.log`.
- Optional demo volume after bootstrap: `.\scripts\seed-demo-data.ps1` / `./scripts/seed-demo-data.sh`.

Manual step-by-step (same outcome) is below if you prefer not to use the bootstrap scripts.

---

## Prerequisites (install once)

| Tool | Version | Why |
|---|---|---|
| **JDK** | 17+ | Compile and run Spring Boot apps |
| **Maven** | 3.8+ | Build, tests, `spring-boot:run` |
| **Docker** | Desktop (Win/Mac), or OrbStack/Colima (Mac) | Postgres, Kafka, Adminer, Kafka UI |
| **Git** | any recent | Clone / sync |
| **Python 3** | optional | Only for [`scripts/demo-firing-alerts.py`](scripts/demo-firing-alerts.py) fallback; macOS bootstrap smoke uses it for JSON |

### Windows — install commands

1. **JDK 17** (Temurin example via winget):

```powershell
winget install EclipseAdoptium.Temurin.17.JDK
# then open a new terminal and confirm:
java -version
```

2. **Maven 3.8+**:

```powershell
winget install Apache.Maven
# new terminal:
mvn -version
```

Or download from https://maven.apache.org/download.cgi and add `bin` to `PATH`.

3. **Docker Desktop** (uses WSL2 on Windows 10/11 Home/Pro):

```powershell
winget install Docker.DockerDesktop
```

Then reboot if prompted, start **Docker Desktop**, wait until it says running, and confirm:

```powershell
docker version
docker compose version
```

If Docker reports *hypervisor is not running*:

- Task Manager → Performance → CPU → **Virtualization: Enabled** (firmware). If Disabled, enable VT-x/AMD-V in BIOS/UEFI.
- In **Administrator** PowerShell: `bcdedit /enum "{current}"` — if `hypervisorlaunchtype` is `Off`, set `bcdedit /set hypervisorlaunchtype Auto` and **restart Windows**.
- Enable features if needed (Admin PowerShell), then reboot:

```powershell
Enable-WindowsOptionalFeature -Online -FeatureName VirtualMachinePlatform -All -NoRestart
Enable-WindowsOptionalFeature -Online -FeatureName Microsoft-Windows-Subsystem-Linux -All -NoRestart
Enable-WindowsOptionalFeature -Online -FeatureName HypervisorPlatform -All -NoRestart
```

- Clear stale docker-machine env (points at dead `192.168.99.100`):

```powershell
[Environment]::SetEnvironmentVariable("DOCKER_HOST", $null, "User")
[Environment]::SetEnvironmentVariable("DOCKER_TLS_VERIFY", $null, "User")
[Environment]::SetEnvironmentVariable("DOCKER_CERT_PATH", $null, "User")
Remove-Item Env:DOCKER_HOST,Env:DOCKER_TLS_VERIFY,Env:DOCKER_CERT_PATH -ErrorAction SilentlyContinue
```

4. **Optional — WSL distro** (helps some Docker Desktop installs):

```powershell
wsl --install -d Ubuntu
# reboot if Windows asks, then:
wsl -l -v
```

### macOS — install commands

```bash
# Homebrew: https://brew.sh
brew install --cask temurin@17
brew install maven
brew install --cask docker   # or: brew install --cask orbstack
java -version
mvn -version
docker version
```

Give Docker Desktop / OrbStack enough RAM (Sonar optional profile needs several GB).

### Working directory tip (important on Windows)

Work from a path that is **ASCII-only** when possible. Non-ASCII folders (e.g. Hebrew) often break OpenAPI codegen under Maven.

```powershell
# Example: copy repo to ASCII path once, then always work from there
robocopy "C:\Users\...\Desktop\...\Mini WSA" C:\Temp\wsa-build /MIR /XD target .git
cd C:\Temp\wsa-build
```

On macOS, UTF-8 paths are usually fine; still quote paths with spaces (`"Mini WSA"`).

---

## Run locally (Windows and macOS)

Do these steps **in order** from the repo root (or `C:\Temp\wsa-build` / `/tmp/wsa-build`).

### 1. Create `.env`

Copy [`.env.example`](.env.example) to `.env`. Set `POSTGRES_PASSWORD` and `SPRING_DATASOURCE_PASSWORD` to the same value. Leave `SONAR_TOKEN` empty until you create a local Sonar token (never commit a real token).

```bash
cp .env.example .env
```

```powershell
Copy-Item .env.example .env
```

### 2. Start infrastructure (Compose)

Starts Postgres `:5432`, Kafka (`apache/kafka:3.8.1`) `:9092`, Adminer `:8082`, Kafka UI `:8083` — **not** Sonar.

```bash
docker compose --env-file .env up -d
docker compose --env-file .env ps
```

Wait until **postgres** and **kafka** show `healthy` (first pull can take several minutes).

Optional one-shot: compose + `mvn clean verify` + image builds:

```powershell
# Windows
.\scripts\validate-clean-checkout.ps1
```

```bash
# macOS / Linux
chmod +x scripts/*.sh generator/*.sh
./scripts/validate-clean-checkout.sh
```

### 3. Build the Java modules

Install jars into `~/.m2` (required before `spring-boot:run` can resolve `contracts`):

```bash
mvn clean install -DskipTests
```

Or run the full test + JaCoCo gate (≥95% LINE and BRANCH):

```bash
mvn clean verify
```

HTML reports after `verify`:

- Aggregate: `coverage/target/site/jacoco-aggregate/index.html`
- Per module: `<module>/target/site/jacoco/index.html`
- Last published numbers: [`QUALITY_REPORT.md`](QUALITY_REPORT.md)

### 4. Load `.env` into the shell (each app terminal)

Spring Boot reads `SPRING_DATASOURCE_*` and `KAFKA_BOOTSTRAP_SERVERS` from the **process environment**. Loading `.env` once per terminal is required.

```powershell
# Windows PowerShell (run in each terminal before spring-boot:run)
Get-Content .env | ForEach-Object {
  if ($_ -match '^\s*#' -or $_ -notmatch '=') { return }
  $k, $v = $_ -split '=', 2
  Set-Item -Path "Env:$k" -Value $v.Trim()
}
$env:KAFKA_BOOTSTRAP_SERVERS = "localhost:9092"
```

```bash
# macOS / Linux (run in each terminal before spring-boot:run)
set -a
source .env
set +a
export KAFKA_BOOTSTRAP_SERVERS=localhost:9092
```

### 5. Start the two apps (two terminals)

**Do not** use `mvn -pl <module> -am spring-boot:run` from the repo root — `-am` pulls the parent POM into the reactor and `spring-boot:run` fails with *Unable to find a suitable main class*.

After step 3 (`install` or `verify`), from the **repo root**, in two separate terminals (each with `.env` loaded):

```bash
# Terminal A — ingest-api :8081
mvn -pl ingest-api spring-boot:run

# Terminal B — analytics-service :8080
mvn -pl analytics-service spring-boot:run
```

Equivalent (also fine):

```bash
cd ingest-api && mvn spring-boot:run
cd analytics-service && mvn spring-boot:run
```

Wait until logs show Tomcat started on **8081** and **8080**. Kafka topics `security-events.raw` / `security-events.dlq` are auto-created on first produce/consume (`KAFKA_TOPIC_RAW` / `KAFKA_TOPIC_DLQ`).

### 6. Smoke-check

| Check | URL / command | Expect |
|---|---|---|
| Ingest health | http://localhost:8081/actuator/health | `"status":"UP"` |
| Analytics health | http://localhost:8080/actuator/health | `"status":"UP"` |
| Hub | http://localhost:8080/ | 200 |
| Ingest Swagger | http://localhost:8081/swagger-ui/index.html | 200 |
| Kafka UI | http://localhost:8083/ | 200 |
| Adminer | http://localhost:8082/ | 200 |

Then post a sample event (see [Try it](#try-it)) — ingest **201**, then summary `totalEvents` ≥ 1 after a few seconds.

### Stop

```bash
# Ctrl+C in both app terminals, then:
docker compose --env-file .env down
```

### Testing strategy

Prefer **MockMvc API/E2E tests** (real Spring context + H2 for analytics) to cover HTTP → service → persistence paths in one place. Keep **focused unit tests** for:

- Domain / manager scoring (`EnrichmentManager`)
- Enrichment idempotency (`EventEnrichmentService`)
- Messaging adapters not loaded under the `test` profile (`KafkaEventProducer`, `DlqPublisher`, `SecurityEventConsumer`)
- Generator CLI parsing and event factory

Avoid duplicating the same scenario in both a thin controller UT and an E2E test.

### Local quality (JaCoCo + SonarQube Community)

Everything runs on your local machine (Windows or macOS): free **JaCoCo** in Maven, free **SonarQube Community** in Docker. No SonarCloud, no paid license. SonarQube needs several GB of Docker RAM on Mac.

| Tool | Role | Gate |
|---|---|---|
| JaCoCo | Line/branch coverage; HTML + XML under `target/site/jacoco*` | `mvn verify` fails if LINE or BRANCH &lt; 95% |
| SonarQube | Code smells, bugs, vulnerabilities, security hotspots; imports JaCoCo coverage | UI review on `http://localhost:9000` (Maven does not wait on Sonar QG) |

Sonar analyzes **`src/main/java` only** (`sonar.sources`), so OpenAPI/MapStruct output under `target/generated-sources` is out of scope without a long exclusion list. JaCoCo still excludes generated class names plus a few mains/CLI/Postgres-only types (see parent `pom.xml`).

```bash
# Coverage + gate (≥95% LINE/BRANCH)
mvn verify

# Local SonarQube (optional Compose profile — keeps daily `up -d` light)
docker compose --profile sonar up -d
# Open http://localhost:9000 — first login admin/admin, set a new password,
# create a user token, put it in .env as SONAR_TOKEN=
```

```powershell
# Windows
mvn verify sonar:sonar "-Dsonar.token=$env:SONAR_TOKEN"
```

```bash
# macOS / Linux
mvn verify sonar:sonar "-Dsonar.token=$SONAR_TOKEN"
```

After a Sonar run, refresh the Sonar rows in [`QUALITY_REPORT.md`](QUALITY_REPORT.md).

### Docker images

From the repo root (both OS):

```bash
docker build -f ingest-api/Dockerfile -t wsa-ingest .
docker build -f analytics-service/Dockerfile -t wsa-analytics .
```

Host-run Java against Compose Kafka is the default path. Container networking for Kafka advertised listeners is left as a follow-up.

## Try it

Ingest a fixture:

```powershell
# Windows PowerShell
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

```bash
# macOS / Linux
curl -sS -X POST http://localhost:8081/v1/events/ingest \
  -H 'Content-Type: application/json' \
  -d @- <<'EOF'
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
EOF
```

A **201** means the event was durably accepted into the ingest outbox (Kafka relay and analytics Postgres write happen asynchronously). Query after a short relay + consumer lag:

```powershell
# Windows
Invoke-RestMethod http://localhost:8080/v1/stats/summary?configId=14227
Invoke-RestMethod "http://localhost:8080/v1/events/samples?category=INJECTION&limit=20&offset=0"
Invoke-RestMethod "http://localhost:8080/v1/stats/timeseries?interval=5m&configId=14227"
```

```bash
# macOS / Linux
curl -sS "http://localhost:8080/v1/stats/summary?configId=14227"
curl -sS "http://localhost:8080/v1/events/samples?category=INJECTION&limit=20&offset=0"
curl -sS "http://localhost:8080/v1/stats/timeseries?interval=5m&configId=14227"
```

Generator (`sql`/`xss` are same-IP + same-path bursts; `mixed` alternates random traffic with bursts):

```powershell
# Windows
.\generator\generate.ps1 -Count 20 -Rate 5 -Wave sql -BurstSize 10
```

```bash
# macOS / Linux
./generator/generate.sh --count 20 --rate 5 --wave sql --burst-size 10
```

### Seed demo data (Kafka + Postgres + alerts)

With infra and both apps already running, one script loads the current demo dataset: mixed generator traffic through ingest → Kafka → analytics, all rules from [`demo/alert-rules.json`](demo/alert-rules.json), then `POST /demo/alerts/fire` so assignment/smoke rules show **FIRING**:

```powershell
# Windows
.\scripts\seed-demo-data.ps1
```

```bash
# macOS / Linux
./scripts/seed-demo-data.sh
```

Defaults to **10,000** mixed events at ~100/s (matches a full local demo volume). Optional flags: PowerShell `-SkipGenerator` / `-EventCount`; bash `--skip-generator` / `--event-count`. Re-runs skip duplicate alert rules.

Alerts are stored in Postgres and survive restarts. `GET /v1/alerts/evaluate` without `alertId` checks every defined rule:

```powershell
# Windows
$alert = Invoke-RestMethod -Method Post -Uri http://localhost:8080/v1/alerts/define -ContentType application/json -Body '{"configId":14227,"category":"INJECTION","metric":"event_count","window":"5m","threshold":10}'
Invoke-RestMethod http://localhost:8080/v1/alerts/evaluate
```

```bash
# macOS / Linux
curl -sS -X POST http://localhost:8080/v1/alerts/define \
  -H 'Content-Type: application/json' \
  -d '{"configId":14227,"category":"INJECTION","metric":"event_count","window":"5m","threshold":10}'
curl -sS http://localhost:8080/v1/alerts/evaluate
```

Stats and samples APIs are limited to **100 requests per IP per minute** (429 when exceeded). Ingest and alerts are not rate limited.

## Storage

Both apps share Compose Postgres database `wsa` (same `SPRING_DATASOURCE_*`). **ingest-api** owns only `ingest_outbox` (Flyway history table `ingest_flyway_schema_history`). **analytics-service** owns enriched events and alert definitions and remains the only writer of `security_events`. Kafka still decouples relay from scoring and carries poison messages to a DLQ.

### Durability / lag monitoring

- Micrometer gauge `ingest_outbox_pending` (pending outbox rows) via ingest actuator: `http://localhost:8081/actuator/metrics/ingest_outbox_pending`
- Kafka UI (`http://localhost:8083/`): open the analytics consumer group / `security-events.raw` topic to watch consumer lag after relay publishes

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

- **201 means durable outbox accept, not enrichment done.** Ingest commits to `ingest_outbox` then returns; a relay publishes to Kafka afterward. Analytics still reads from Kafka and writes enriched rows to Postgres. Clients should not treat 201 as “row visible in stats yet.”
- **Two services need clear ownership.** Ingest owns only `ingest_outbox` on the shared `wsa` database. Analytics owns enrichment tables and the score formula. The shared `contracts` jar only holds the JSON shape, so changing how we score attacks does not force a change to the message type.
- **SQL is a good fit for the stats APIs.** Summary, top attackers, and time buckets are plain queries (`GROUP BY`, `AVG`, `date_bin`). We did not need a separate analytics product for this size of data.
- **Idempotency matters with Kafka.** The same `eventId` can show up twice (retry, redelivery). We skip insert when the id already exists, so duplicates do not inflate counts.
- **API tests cover more than thin unit tests for the same path.** MockMvc E2E against the real Spring stack (H2 for analytics) exercises controller → service → repository together; keep unit tests for scoring, enrichment edge cases, and Kafka adapters.

## Challenges

- **Kafka advertised listeners (Windows and macOS).** Getting the host app to talk to Kafka inside Docker was the hardest setup step. The broker must advertise an address the host can reach (`localhost:9092`). Wrong advertised listeners meant produce/consume hung or failed even when the container was healthy. Colima users may need to override `KAFKA_ADVERTISED_LISTENERS` in `.env`.
- **Postgres-only SQL in tests.** Timeseries uses `date_bin`, which H2 does not support the same way. Production uses `PostgresTimeseriesBucketQuery`; the `test` profile uses `InMemoryTimeseriesBucketQuery`.
- **Host env vars leaking into tests.** A local `SPRING_DATASOURCE_URL` pointed tests at real Postgres while the test profile expected H2. We fixed that by setting the H2 URL on the Spring Boot test annotations.
- **Non-ASCII project paths + OpenAPI codegen.** On some Windows setups, Maven under a Hebrew/non-ASCII folder fails to load generated classes. Build/test from an ASCII copy (`C:\Temp\wsa-build` or `/tmp/wsa-build`) when that happens. macOS usually handles UTF-8 paths fine; still quote paths that contain spaces.

## If we had more time

Platform and reliability:

- ~~**Stronger event durability**~~ **Done:** ingest transactional outbox before HTTP 201, async relay to Kafka, all-or-nothing batch accept, sync-wait DLQ publish, `ingest_outbox_pending` metric + Kafka UI for lag. Analytics inserts stay idempotent by `event_id`. Client-visible processing statuses remain optional later.
- **S3 (or similar object storage) for long-term event retention.** Archive older enriched events out of PostgreSQL into cheap object storage so hot query counts and table size stay bounded, while historical raw/enriched data remains available for audit or offline analysis.
- Add an end-to-end test: post an event to ingest, wait for the consumer, assert a row and a summary count.
- Build a small web page that charts `/v1/stats/timeseries` and shows `/v1/stats/summary`.
- Move DB (and any future API) credentials out of env files into a secret manager (e.g. Vault, cloud secret store, or Kubernetes Secrets), and inject them at runtime instead of keeping passwords/keys on disk or in plain process env.

AI / LLM (on top of the current pipeline, not instead of it):

Today **classify** is a fixed map from `rule.category`, and **threatScore** is a fixed formula. Those should stay as they are: fast, cheap, and easy to test. AI would help analysts *use* the stored events, not replace those two steps.

- **Semantic search over past events.** Embed fields such as path, rule message, and user agent. When an analyst opens one event, return “similar past events” so they can see if this looks like a known campaign. A small **reranker** model can reorder the top candidates so the most useful matches rise first.
- **LLM explanations, not scores.** Keep `threatScore` from the formula. Ask an LLM to write a short plain-language note: what happened, why the score is high, and what to check next. That helps SOC users without making scores random or hard to reproduce.
- **Natural-language queries on top of SQL stats.** Let someone ask “top IPs attacking /login last hour” and turn that into calls to summary / samples / timeseries, instead of writing query params by hand.
- **Optional second opinion on edge cases.** For odd free-text (weird user agents, unusual paths) an LLM or embedding-based check could flag “looks unusual vs recent traffic” as an extra field. It would not overwrite category or threat score.
