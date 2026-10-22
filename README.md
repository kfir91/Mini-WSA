# WSA

Local security analytics pipeline for WAF-style DLR events.

1. Clients (or the included **generator**) `POST` events to **ingest-api**.
2. Ingest validates, stamps `receivedAt`, writes a **transactional outbox** row, and returns **HTTP 201**.
3. A relay publishes from the outbox to Kafka.
4. **analytics-service** consumes the topic, classifies, scores threat, stores in PostgreSQL, and serves query APIs.

**Stack:** Java 17+, Maven 3.8+, Docker (Desktop on Windows/macOS, or OrbStack/Colima on macOS), PostgreSQL 16, Kafka KRaft, HashiCorp Vault (Community). No cloud databases. Runs on **Windows and macOS**.

## Contents

- [Architecture](#architecture)
  - [Secrets (HashiCorp Vault)](#secrets-hashicorp-vault)
- [Client UI (React SPA)](#client-ui-react-spa)
- [Transactional Outbox](#transactional-outbox)
- [Quick start (bootstrap)](#quick-start-bootstrap)
- [Prerequisites](#prerequisites-install-once)
- [Run manually](#run-manually)
- [Try it](#try-it)
- [Storage](#storage)
- [Threat score](#threat-score)
- [Testing and quality](#testing-and-quality)
- [Tags](#tags)
- [Future ideas](#future-ideas)

## Architecture

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
   security-events.dlq  <-----------  (poison / exhausted retries)
              |                         |
              |                         |  consume → classify/score → Postgres
              |                         v
              |                    PostgreSQL 16
              |                         |
              |                         |  GET summary / samples / timeseries / alerts
              |                         v
              |                    analytics APIs (:8080) / Swagger UI
              |                         ^
              |                         |  proxy /v1/** + /demo/**
              |                         |
              |                    client :8084
              |                    React hub (/) + Stats UI (/stats)
              |
   Infra (Compose): Postgres :5432, Kafka :9092, Vault :8200,
                    Adminer :8082, Kafka UI :8083
   Optional: SonarQube Community :9000 (Compose profile `sonar`)
```

### Secrets (HashiCorp Vault)

Credentials are an **infrastructure/config** concern — **outside** `Controller → Service → Repository`. Business layers keep reading `spring.datasource.password` from the Spring `Environment`; they never call Vault APIs.

| Store | Holds |
|---|---|
| Vault KV v2 `secret/wsa/local` | `postgres_password`, `spring.datasource.password` (same value), `sonar_token` |
| `.env` (non-secrets) | JDBC URL/username, Kafka, Adminer URL/server, `VAULT_URI` |
| `.vault/` (gitignored) | AppRole `role_id`/`secret_id`, persisted `postgres_password` (survives Vault `-dev` restarts) |

```text
  scripts/vault-seed.* ──► Vault KV secret/wsa/local (AppRole)
         │                      │
         │                      ├── Spring Cloud Vault (AppRole login)
         │                      │         └── spring.datasource.password
         │                      │                   └── ingest + analytics datasources
         └── ephemeral shell ──► Compose Postgres / Adminer env at `up` time only
```

- **Auth (local):** AppRole role `wsa-local`. Production systems often swap to K8s/AWS/Azure auth with the same KV paths (not used here — no K8s/AWS in this project).
- **Apps:** Spring Cloud Vault only (no Eureka). No `SPRING_DATASOURCE_PASSWORD` in process env; password comes from Vault key `spring.datasource.password`.
- **Adminer:** `/adminer-go/config` (analytics) returns URL/server/user/db only — **never** the password. PHP autologin inside the Adminer container uses Compose-injected secrets.
- **Tests:** Parent Surefire sets `VAULT_ENABLED=false` (and e2e Failsafe under `-Pe2e`); apps also use `spring.cloud.vault.enabled=false` in test/`e2e` profiles. H2 for module tests; Testcontainers only in `e2e`. Default `mvn verify` stays Docker-free for the JaCoCo gate.
- **Hygiene:** Never put DB passwords or Sonar tokens in `.env`. Never commit `.env` or `.vault/`. Local Compose still passes the DB password into Postgres/Adminer **container** env at `up` time (Docker limitation).
- **Bootstrap:** `scripts/vault-seed.ps1` / `vault-seed.sh` run via `docker compose exec` (no host Vault CLI).

### Modules

| Module | Port | Owns | Does not own |
|---|---|---|---|
| `contracts` | — | Wire models from OpenAPI (`SecurityEventMessage`, enums) | Scoring, JPA, Spring, query DTOs |
| `ingest-api` | 8081 | Validate, stamp `receivedAt`, outbox, Kafka relay, ingest errors | Enrichment DB, classify/score, alerts |
| `analytics-service` | 8080 | Consume Kafka, enrich, Postgres, read APIs, alerts, rate limit on stats/samples | Ingest HTTP, browser UI |
| `client` | 8084 | React hub + Stats SPA; proxies `/v1/**` and `/demo/**` to analytics | Business logic, DB, Kafka |
| `generator` | — | CLI synthetic waves (uses `contracts` jar) | Persistence, OpenAPI codegen |
| `e2e` | — | Opt-in `EventFlowIT` (Testcontainers Postgres + Kafka); profile `-Pe2e` | Default JaCoCo gate / unit suite |
| `coverage` | — | Aggregated JaCoCo report after `mvn verify` | Application code |

### Layers

**ingest-api:** OpenAPI → `controller` → `service` → outbox `entity`/`repository` + Kafka `client` (relay). No domain/enrichment schema.

**analytics-service:** OpenAPI → `controller` → `service` → `manager`/`domain` → `repository`/`entity`. MapStruct mappers sit between API/contract and entity. Kafka consumer/DLQ are adapters only.

**client:** React SPA (`client/frontend/` → generated `static/`) + thin reverse proxy to analytics (`AnalyticsProxyController`). No domain, JPA, or OpenAPI ownership.

```text
api (generated) ──> controller ──> service ──> manager/domain
                                      │
                                   mapper
                                      │
                                   entity/repository
```

Constructor injection is the default. Controllers stay thin.

### Same contract on the wire

- HTTP ingest body and Kafka payload both use `SecurityEventMessage` from `contracts`.
- `attackType` and `threatScore` exist only after analytics enrichment (not on the wire).
- Rate limit: **100 requests per IP per minute** on `/v1/stats/**` and `/v1/events/samples` only (HTTP 429). Ingest and alerts are not limited.

### OpenAPI / Swagger UI

Edit YAML under `src/main/resources/open-api/`, then `mvn generate-sources` (also runs during `compile` / `verify`).

- Ingest: http://localhost:8081/swagger-ui/index.html
- Analytics: http://localhost:8080/swagger-ui/index.html

Use `/swagger-ui/index.html` (not bare `/swagger-ui.html`). Restart apps after OpenAPI or springdoc changes.

### Local hub (`client` service)

- Primary UI: http://localhost:8084/ (hub) and http://localhost:8084/stats when **client** is running
- Repo-root [`index.html`](index.html) only redirects to `:8084` (do not open it as a standalone hub)

Browser calls to `/v1/**` and `/demo/**` are **proxied** by client → analytics (`wsa.client.analytics-base-url` / `WSA_ANALYTICS_BASE_URL`, default `http://localhost:8080`). **Adminer:** hub link → http://localhost:8080/adminer-go.html (owned by analytics). Do not hand-build a `localhost:8082/?pgsql=postgres&…` URL with the wrong server name.

### Client UI (React SPA)

Same-origin **React + TypeScript (Vite)** app owned by the **`client`** module. Hub covers service links, alerts, rate-limit demos, and **seed demo events** (configurable count); Stats charts `GET /v1/stats/timeseries` and renders `GET /v1/stats/summary`.

| | |
|---|---|
| URLs | http://localhost:8084/ (shared shell + sidebar) · `/apis` · `/alerts` · `/rate` · `/infra` · `/stats` |
| Source | [`client/frontend/`](client/frontend/) — see also [`client/frontend/README.md`](client/frontend/README.md) |
| Build output | `client/src/main/resources/static/` (**generated** — gitignored; do not hand-edit) |
| Maven | `frontend-maven-plugin` runs `yarn install` + `yarn build` on `generate-resources`. Skip with `-Dfrontend.skip=true` (property on `client/pom.xml`) when `static/` is already built |
| Layers | Browser → React SPA + `AnalyticsProxyController` → analytics; `POST /hub/seed/events` → ingest-api |
| Config | Ports/paths/defaults in [`appConfig.ts`](client/frontend/src/config/appConfig.ts); optional `VITE_WSA_HOST`, `VITE_WSA_*_PORT`; `WSA_INGEST_BASE_URL` for hub seed |
| i18n | UI copy in [`en.json`](client/frontend/src/i18n/locales/en.json) via `react-i18next` |
| Chart | `chart.js` / `react-chartjs-2` (npm) |
| Dev | `cd client/frontend && yarn && yarn dev` (Vite proxies `/v1` + `/demo` → analytics, `/hub` → client; ports from `VITE_WSA_*`) |
| Constraints | Open via `:8084` (or Vite). Proxied stats/samples count toward analytics **100 req/IP/min** |

Frontend layout: `api/` (HTTP) → `hooks/` → `pages/` + `components/`; Spring only serves the built SPA and proxies APIs.

Stats filters: optional `configId`, `interval` (`1m` / `5m` / `1h`), optional `from` / `to` (defaults from `appConfig`).

## Transactional Outbox

**Problem:** Publishing to Kafka inside the HTTP request can leave the system inconsistent if the process crashes or the broker is down (accepted but never published, or the opposite).

**Solution:** Persist first, publish later.

| Step | What happens |
|---|---|
| 1. Accept | `EventIngestService` validates, stamps `receivedAt`, inserts a `PENDING` row into `ingest_outbox` (unique `event_id`) in one DB transaction, then returns **HTTP 201**. |
| 2. Relay | `OutboxRelayService` polls every ~1s (`wsa.outbox.poll-interval-ms`). It loads pending rows with `FOR UPDATE SKIP LOCKED`, then publishes each payload to Kafka **outside** any DB transaction. |
| 3. Outcome | Success → `PUBLISHED`. Failure → bump `attempt_count`, stay `PENDING` until `max-attempts` (default 5) → `FAILED`. |

**HTTP 201** means “durably accepted into the outbox,” not “already on Kafka” and not “already in analytics Postgres.” The 201 body is `IngestAcceptResponse` with per-`eventId` client status (usually `accepted`). Duplicate `eventId` values are idempotent (unique constraint; re-POST returns the **current** mapped status for that id). Backlog: Micrometer gauge `ingest_outbox_pending` on ingest actuator.

**Client-visible status** (`GET /v1/events/{eventId}/status`, hub: `GET /hub/events/{eventId}/status`):

| Client status | Outbox |
|---|---|
| `accepted` | `PENDING`, `attempt_count = 0` |
| `progressing` | `PENDING`, `attempt_count > 0` |
| `done` | `PUBLISHED` (Kafka ack) |
| `failed` | `FAILED` |

This is an outbox projection only — **not** analytics enrichment.

```text
  POST /v1/events/ingest
        │
        ▼
  validate + stamp receivedAt
        │
        ▼
  INSERT ingest_outbox (PENDING)  ──TX──►  HTTP 201
        │
        │  (later, OutboxRelayService)
        ▼
  load PENDING (SKIP LOCKED) → Kafka produce → PUBLISHED
                                      └ retries exhausted → FAILED
```

Config defaults (`application.yml` / env): `OUTBOX_POLL_INTERVAL_MS=1000`, `OUTBOX_BATCH_SIZE=50`, `OUTBOX_MAX_ATTEMPTS=5`, `OUTBOX_RELAY_ENABLED=true`.

## Quick start (bootstrap)

From the repo root, these scripts install missing tools when possible, seed Vault, start Compose, build, run **analytics → ingest → client**, and smoke-test until localhost answers.

```powershell
# Windows (PowerShell)
.\scripts\bootstrap-windows.ps1
```

```bash
# macOS
chmod +x scripts/*.sh generator/*.sh
./scripts/bootstrap-macos.sh
```

| Windows | macOS | Meaning |
|---|---|---|
| `-SkipInstall` | `--skip-install` | Skip winget/brew (tools already present) |
| `-FullVerify` | `--full-verify` | `mvn clean verify` (JaCoCo ≥95%) instead of `install -DskipTests` |
| `-SkipApps` | `--skip-apps` | Build + infra only |
| `-SkipCompose` | `--skip-compose` | Skip `docker compose up` |
| `-ResetData` | `--reset-data` | `docker compose down -v` + re-seed Vault (wipes local Postgres; fixes password drift) |

**Notes**

- First Docker Desktop install may need a **Windows reboot**. Re-run with `-SkipInstall` once Docker is running.
- On Windows, if the checkout path is non-ASCII (e.g. Hebrew), bootstrap syncs Maven/Compose work to `C:\Temp\wsa-build`. **Git stays in the original folder.** Prefer an ASCII path when you can (OpenAPI codegen is happier). `.vault/` is preserved across that sync so the Postgres password stays aligned with the Compose volume.
- Bootstrap loads `POSTGRES_PASSWORD` from `.vault/postgres_password` into the shell before `docker compose up`, verifies Postgres accepts it, and auto-recreates the volume if it does not (same effect as `-ResetData` when drift is detected).
- Before build/start it stops whatever is listening on the app ports (avoids locked `*-exec.jar` and stale broken JVMs). Apps start in order: **analytics → ingest → client**.
- App process env does **not** carry `SPRING_DATASOURCE_PASSWORD`; Spring reads the DB password from Vault via AppRole.
- App logs: `target/bootstrap-analytics.log`, `target/bootstrap-ingest.log`, `target/bootstrap-client.log`.
- Hub: http://localhost:8084/ after bootstrap (not repo-root `index.html` as a standalone page).
- Optional demo load after bootstrap: `.\scripts\seed-demo-data.ps1` / `./scripts/seed-demo-data.sh`.

Manual steps below if you prefer not to use bootstrap.

---

## Prerequisites (install once)

| Tool | Version | Why |
|---|---|---|
| **JDK** | 17+ | Compile and run Spring Boot |
| **Maven** | 3.8+ | Build, tests, `spring-boot:run` |
| **Docker** | Desktop (Win/Mac), or OrbStack/Colima (Mac) | Postgres, Kafka, Adminer, Kafka UI |
| **Git** | recent | Clone / sync |
| **Python 3** | optional | Demo-alert fallback script; macOS bootstrap smoke uses it for JSON |

### Windows

```powershell
winget install EclipseAdoptium.Temurin.17.JDK
winget install Apache.Maven
winget install Docker.DockerDesktop
# new terminal:
java -version
mvn -version
docker version
docker compose version
```

Reboot if Docker asks, start **Docker Desktop**, wait until it is running.

If Docker reports *hypervisor is not running*:

- Task Manager → Performance → CPU → **Virtualization: Enabled** (else enable VT-x/AMD-V in BIOS).
- Admin PowerShell: if `bcdedit /enum "{current}"` shows `hypervisorlaunchtype Off`, run `bcdedit /set hypervisorlaunchtype Auto` and restart.
- Enable features if needed, then reboot:

```powershell
Enable-WindowsOptionalFeature -Online -FeatureName VirtualMachinePlatform -All -NoRestart
Enable-WindowsOptionalFeature -Online -FeatureName Microsoft-Windows-Subsystem-Linux -All -NoRestart
Enable-WindowsOptionalFeature -Online -FeatureName HypervisorPlatform -All -NoRestart
```

- Clear stale **Docker Toolbox** / `docker-machine` env (old `192.168.99.100`). Bootstrap’s `Clear-StaleDockerEnv` also drops `DOCKER_MACHINE_NAME` / `DOCKER_TOOLBOX_INSTALL_PATH` and prefers Docker Desktop’s named pipe when present:

```powershell
[Environment]::SetEnvironmentVariable("DOCKER_HOST", $null, "User")
[Environment]::SetEnvironmentVariable("DOCKER_TLS_VERIFY", $null, "User")
[Environment]::SetEnvironmentVariable("DOCKER_CERT_PATH", $null, "User")
[Environment]::SetEnvironmentVariable("DOCKER_MACHINE_NAME", $null, "User")
[Environment]::SetEnvironmentVariable("DOCKER_TOOLBOX_INSTALL_PATH", $null, "User")
Remove-Item Env:DOCKER_HOST,Env:DOCKER_TLS_VERIFY,Env:DOCKER_CERT_PATH,Env:DOCKER_MACHINE_NAME,Env:DOCKER_TOOLBOX_INSTALL_PATH -ErrorAction SilentlyContinue
# Prefer Desktop engine if the pipe exists:
# $env:DOCKER_HOST = "npipe:////./pipe/dockerDesktopLinuxEngine"
```

Optional WSL distro: `wsl --install -d Ubuntu`, then reboot if Windows asks.

### macOS

```bash
brew install --cask temurin@17
brew install maven
brew install --cask docker   # or: brew install --cask orbstack
java -version && mvn -version && docker version
```

Give Docker / OrbStack enough RAM if you use the Sonar profile (several GB).

### Path tip (Windows)

Non-ASCII folders often break OpenAPI codegen. Example one-time sync:

```powershell
robocopy "C:\Users\...\Desktop\...\Mini WSA" C:\Temp\wsa-build /MIR /XD target .git .vault
cd C:\Temp\wsa-build
```


On macOS, quote paths with spaces (`"Mini WSA"`).

---

## Run manually

Do these steps **in order** from the repo root (or `C:\Temp\wsa-build`).

### 1. Create `.env`

Copy [`.env.example`](.env.example) to `.env`. It holds **non-secrets only** (URLs, usernames, Kafka, Vault URI). DB password and Sonar token live in Vault (see [Secrets](#secrets-hashicorp-vault)). Never commit `.env` or `.vault/`.

```powershell
Copy-Item .env.example .env
```

```bash
cp .env.example .env
```

### 2. Seed Vault and start infrastructure

```powershell
# Windows
Get-Content .env | ForEach-Object {
  if ($_ -match '^\s*#' -or $_ -notmatch '=') { return }
  $k, $v = $_ -split '=', 2
  Set-Item -Path "Env:$k" -Value $v.Trim()
}
.\scripts\vault-seed.ps1
$env:POSTGRES_PASSWORD = (Get-Content .vault\postgres_password -Raw).Trim()
$env:ADMINER_PASSWORD = $env:POSTGRES_PASSWORD
docker compose --env-file .env up -d
docker compose --env-file .env ps
```

```bash
# macOS / Linux
set -a; source .env; set +a
./scripts/vault-seed.sh
export POSTGRES_PASSWORD="$(tr -d '\r\n' <.vault/postgres_password)"
export ADMINER_PASSWORD="$POSTGRES_PASSWORD"
docker compose --env-file .env up -d
docker compose --env-file .env ps
```

Starts Vault `:8200`, Postgres `:5432`, Kafka `:9092`, Adminer `:8082`, Kafka UI `:8083` — **not** Sonar. Wait until **vault**, **postgres**, and **kafka** are `healthy`.

If you previously used a different Postgres password in a volume, keep `.vault/postgres_password` stable **or** run `docker compose down -v` once (data loss) after a fresh seed — Postgres applies `POSTGRES_PASSWORD` only on first volume init. On a brand-new empty database, start **analytics-service before ingest-api** so Flyway owns the shared schema cleanly (ingest uses a separate history table for outbox).

Optional one-shot (vault seed + compose + `mvn clean verify` + image builds): `.\scripts\validate-clean-checkout.ps1` / `./scripts/validate-clean-checkout.sh`.

### 3. Build

```bash
mvn clean install -DskipTests
```

Or full tests + JaCoCo gate (≥95% LINE and BRANCH):

```bash
mvn clean verify
# Optional: skip Yarn if client static/ is already generated
# mvn clean verify -Dfrontend.skip=true
```

Reports after `verify`:

- Aggregate: `coverage/target/site/jacoco-aggregate/index.html`
- Per module: `<module>/target/site/jacoco/index.html`
- Snapshot: [`QUALITY_REPORT.md`](QUALITY_REPORT.md)

### 4. Load `.env` + AppRole into each app terminal

Spring Boot loads the DB password from Vault via Spring Cloud Vault (AppRole). Load non-secrets from `.env` and AppRole ids from `.vault/approle.env` (created by vault-seed).

```powershell
# Windows PowerShell (each terminal before spring-boot:run)
Get-Content .env, .vault\approle.env | ForEach-Object {
  if ($_ -match '^\s*#' -or $_ -notmatch '=') { return }
  $k, $v = $_ -split '=', 2
  Set-Item -Path "Env:$k" -Value $v.Trim()
}
$env:KAFKA_BOOTSTRAP_SERVERS = "localhost:9092"
```

```bash
# macOS / Linux
set -a
source .env
source .vault/approle.env
set +a
export KAFKA_BOOTSTRAP_SERVERS=localhost:9092
```

### 5. Start apps (three terminals)

**Do not** use `mvn -pl <module> -am spring-boot:run` from the repo root — `-am` pulls the parent POM into the reactor and fails with *Unable to find a suitable main class*.

After step 3, from the **repo root** (load `.env` + `.vault/approle.env` for ingest/analytics; client only needs `.env` / defaults — no Vault):

```bash
# Terminal A — analytics-service :8080  (start first on a fresh DB)
mvn -pl analytics-service spring-boot:run

# Terminal B — ingest-api :8081
mvn -pl ingest-api spring-boot:run

# Terminal C — client :8084 (React hub + Stats; build already generated static/)
mvn -pl client spring-boot:run
```

Or `cd <module> && mvn spring-boot:run`. Client uses `WSA_ANALYTICS_BASE_URL` (default `http://localhost:8080`).

Wait until Tomcat is up on **8080**, **8081**, and **8084**. Topics `security-events.raw` / `security-events.dlq` are auto-created on first produce/consume (`KAFKA_TOPIC_RAW` / `KAFKA_TOPIC_DLQ`).

### 6. Smoke-check

| Check | URL | Expect |
|---|---|---|
| Ingest health | http://localhost:8081/actuator/health | `"status":"UP"` |
| Analytics health | http://localhost:8080/actuator/health | `"status":"UP"` |
| Client health | http://localhost:8084/actuator/health | `"status":"UP"` |
| Hub (React) | http://localhost:8084/ | 200 |
| Stats UI | http://localhost:8084/stats | 200 |
| Ingest Swagger | http://localhost:8081/swagger-ui/index.html | 200 |
| Kafka UI | http://localhost:8083/ | 200 |
| Adminer | http://localhost:8082/ (or hub → analytics `/adminer-go.html`) | 200; config JSON has **no** `password` |

Then post a sample event ([Try it](#try-it)) — ingest **201**, then summary `totalEvents` ≥ 1 after a few seconds (relay + consumer lag). Open Stats on `:8084/stats` to chart the same data.

### Stop

```bash
# Ctrl+C in app terminals, then:
docker compose --env-file .env down
```

### Docker images (optional)

```bash
docker build -f ingest-api/Dockerfile -t wsa-ingest .
docker build -f analytics-service/Dockerfile -t wsa-analytics .
```

Default local path is host JVM + Compose Kafka. Running the apps as containers against Compose Kafka advertised listeners is a follow-up.

## Try it

### Ingest one event

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

**201** = durable outbox accept (Kafka + analytics DB are async). After a short wait:

```powershell
Invoke-RestMethod http://localhost:8080/v1/stats/summary?configId=14227
Invoke-RestMethod "http://localhost:8080/v1/events/samples?category=INJECTION&limit=20&offset=0"
Invoke-RestMethod "http://localhost:8080/v1/stats/timeseries?interval=5m&configId=14227"
```

```bash
curl -sS "http://localhost:8080/v1/stats/summary?configId=14227"
curl -sS "http://localhost:8080/v1/events/samples?category=INJECTION&limit=20&offset=0"
curl -sS "http://localhost:8080/v1/stats/timeseries?interval=5m&configId=14227"
```

Or open the Stats UI: http://localhost:8084/stats (summary tables + timeseries chart; client proxies to analytics).

### Generator

`sql` / `xss` = same-IP + same-path bursts; `mixed` alternates random traffic with bursts.

```powershell
.\generator\generate.ps1 -Count 20 -Rate 5 -Wave sql -BurstSize 10
```

```bash
./generator/generate.sh --count 20 --rate 5 --wave sql --burst-size 10
```

### Seed demo data

With infra plus **ingest-api** and **analytics-service** running (client optional): mixed traffic through ingest → Kafka → analytics, rules from [`scripts/alert-rules.json`](scripts/alert-rules.json), then `POST /demo/alerts/fire` so assignment/smoke rules show **FIRING**.

```powershell
.\scripts\seed-demo-data.ps1
```

```bash
./scripts/seed-demo-data.sh
```

Default: **10,000** mixed events at ~100/s. Flags: `-SkipGenerator` / `-EventCount` (PowerShell), `--skip-generator` / `--event-count` (bash). Re-runs skip duplicate alert rules.

From the hub UI (Overview): set **Event count** and click **Seed events** — calls `POST /hub/seed/events` on the client service (1–10,000), which batch-posts synthetic events to ingest-api.

### Alerts

Alerts live in Postgres and survive restarts. `GET /v1/alerts/evaluate` without `alertId` evaluates every defined rule.

```powershell
$alert = Invoke-RestMethod -Method Post -Uri http://localhost:8080/v1/alerts/define -ContentType application/json -Body '{"configId":14227,"category":"INJECTION","metric":"event_count","window":"5m","threshold":10}'
Invoke-RestMethod http://localhost:8080/v1/alerts/evaluate
```

```bash
curl -sS -X POST http://localhost:8080/v1/alerts/define \
  -H 'Content-Type: application/json' \
  -d '{"configId":14227,"category":"INJECTION","metric":"event_count","window":"5m","threshold":10}'
curl -sS http://localhost:8080/v1/alerts/evaluate
```

## Storage

Both apps share Compose database `wsa` (same `SPRING_DATASOURCE_*`).

| Owner | Tables | Flyway history |
|---|---|---|
| **ingest-api** | `ingest_outbox` only | `ingest_flyway_schema_history` |
| **analytics-service** | `security_events`, `alert_definitions` (only writer of enriched events) | `flyway_schema_history` |

Kafka decouples outbox relay from scoring. Poison / exhausted-retry messages go to `security-events.dlq` (analytics uses `ErrorHandlingDeserializer` so poison JSON becomes a `DeserializationException` → DLQ; DLQ publish waits for broker ack so the consumer does not advance without a durable DLQ write).

### Monitoring lag

- Outbox backlog: http://localhost:8081/actuator/metrics/ingest_outbox_pending
- Consumer lag: Kafka UI http://localhost:8083/ → analytics consumer group / `security-events.raw`

## Threat score

Computed only in analytics `EnrichmentManager` (cap 100; current formula max is 90):

| Factor | Points |
|---|---|
| Severity CRITICAL / HIGH / MEDIUM / LOW | 40 / 30 / 20 / 10 |
| Action DENY / ALERT / MONITOR | 20 / 10 / 0 |
| Path contains `/admin` or `/login` (case-insensitive) | +15 |
| Same `clientIp` already has **more than 5** events in the last 10 minutes | +15 |

**Attack type** (from `rule.category`): INJECTION → SQL/Command Injection, XSS → Cross-Site Scripting, PROTOCOL_VIOLATION → Protocol Anomaly, DATA_LEAKAGE → Data Exfiltration, BOT → Bot Activity, DOS → Denial of Service, RATE_LIMIT → Rate Limiting.

## Testing and quality

### Tests

Prefer **MockMvc API/E2E tests** (real Spring context + in-memory **H2** in PostgreSQL mode) for HTTP → service → persistence. Keep focused unit tests for:

- Scoring / classify (`EnrichmentManager`)
- Enrichment idempotency (`EventEnrichmentService`)
- Analytics messaging adapters (`DlqPublisher`, `SecurityEventConsumer` use `@Profile("!test")`)
- Generator CLI parsing and event factory
- Outbox relay behavior (`OutboxRelayServiceTest`)

Avoid duplicating the same scenario in both a thin controller unit test and an E2E test.

**Cross-service event flow** (real Postgres + Kafka via Testcontainers) lives in module `e2e` under Failsafe profile `-Pe2e`. It is **not** part of default `mvn verify` (keeps the JaCoCo gate Docker-free).

| IT class | What it asserts |
|---|---|
| `EventFlowIT` | Happy path ingest → outbox `PUBLISHED` → samples/summary + poll-based idempotent re-POST |
| `ClientProxyPipelineIT` | Client BFF in the loop: hub seed → proxied `/v1` summary/samples → hub status `done` |
| `DlqPathIT` | Poison JSON → Kafka DLQ (+ headers); bad outbox payload → `FAILED` (no DLQ) |
| `BacklogLagIT` | Multi-event batch: outbox pending → 0, `ingest_outbox_pending` → 0, Kafka lag → 0, samples catch-up |

```bash
mvn -pl e2e -am verify -Pe2e
# Optional faster local loop when static/ already exists:
# mvn -pl e2e -am verify -Pe2e -Dfrontend.skip=true -Djacoco.skip=true
```

| Requirement | Note |
|---|---|
| Docker | **Docker Desktop** (or compatible engine). Without Docker, pipeline ITs are skipped (`assumeTrue` Docker available). |
| Windows | Leftover Docker Toolbox / `DOCKER_MACHINE_NAME` breaks Testcontainers — clear it (see [Prerequisites](#windows)) or use bootstrap. Failsafe pins `DOCKER_HOST=npipe:////./pipe/dockerDesktopLinuxEngine`. |
| macOS / Linux | If needed: `DOCKER_HOST=unix:///var/run/docker.sock` (override the Windows pipe set in `e2e` Failsafe). |
| Profile | Spring profile `e2e` — **never** `test` (analytics Kafka consumer is `@Profile("!test")`). |

Boot runnable jars are packaged as `*-exec.jar` (thin main artifact stays dependency-friendly for the shared e2e classpath).

### JaCoCo + SonarQube (local)

Free local tools only — no SonarCloud. Sonar needs several GB of Docker RAM.

| Tool | Role | Gate |
|---|---|---|
| JaCoCo | Line/branch coverage under `target/site/jacoco*` | `mvn verify` fails if LINE or BRANCH &lt; 95% |
| SonarQube Community | Smells, bugs, vulnerabilities; imports JaCoCo | Review at http://localhost:9000 (Maven does not wait on a quality gate) |

Sonar analyzes `src/main/java` only. JaCoCo excludes generated classes and a few mains/CLI types (see parent `pom.xml`).

```bash
mvn verify

# Optional Sonar profile
docker compose --profile sonar up -d
# http://localhost:9000 — first login admin/admin, set password, create a user token
# Store it in Vault (and optional .vault/sonar_token for re-seed), not in .env:
#   printf '%s' 'YOUR_TOKEN' > .vault/sonar_token
#   .\scripts\vault-seed.ps1   # or ./scripts/vault-seed.sh
```

```powershell
# Uses VAULT_DEV_ROOT_TOKEN_ID from .env (seed/dev only — not used by Spring apps)
$token = if ($env:VAULT_DEV_ROOT_TOKEN_ID) { $env:VAULT_DEV_ROOT_TOKEN_ID } else { "wsa-dev-root" }
$env:SONAR_TOKEN = (docker compose --env-file .env exec -T `
  -e VAULT_ADDR=http://127.0.0.1:8200 -e "VAULT_TOKEN=$token" `
  vault vault kv get -field=sonar_token secret/wsa/local).Trim()
mvn verify sonar:sonar "-Dsonar.token=$env:SONAR_TOKEN"
```

```bash
# Uses VAULT_DEV_ROOT_TOKEN_ID from .env (seed/dev only — not used by Spring apps)
token="${VAULT_DEV_ROOT_TOKEN_ID:-wsa-dev-root}"
export SONAR_TOKEN="$(docker compose --env-file .env exec -T \
  -e VAULT_ADDR=http://127.0.0.1:8200 -e "VAULT_TOKEN=${token}" \
  vault vault kv get -field=sonar_token secret/wsa/local | tr -d '\r\n')"
mvn verify sonar:sonar "-Dsonar.token=$SONAR_TOKEN"
```

Refresh Sonar rows in [`QUALITY_REPORT.md`](QUALITY_REPORT.md) after a Sonar run.

## Tags

| Tag | Meaning |
|---|---|
| `v0.1-ingestion` … `v0.9-platform` | Incremental milestones (ingest → enrichment → stats → samples → timeseries → alerts → rate limit → generator → docs/Docker) |
| `v1.0-quality` | JaCoCo ≥95% gate + local SonarQube Community |

## Future ideas

**Platform**

- Object storage (e.g. S3) for long-term retention of older enriched events.
- Broader pipeline ITs for rate-limit / alerts / timeseries (same `e2e` harness as client/DLQ/backlog suites).

**AI / RAG (analyst layer only — never on the ingest/enrich hot path)**

Keep fixed category → attack type and the threat formula (fast, cheap, testable). Do **not** use RAG/LLM to set `threatScore`, overwrite category, or decide “malware vs not” — the wire event has no payload/hash/intel for that, and scoring must stay deterministic for tests.

Useful additions (after a samples / investigation UI exists; demo generator text is too canned for semantics until messages/paths vary more):

1. **Similar past events** — embed a short fingerprint per enriched event (`category`, `attack_type`, `rule_name`, `path`, `action`, geo) and return top-K neighbors for an event the analyst opened. Classic RAG retrieval; strengthens triage, not scoring.
2. **Alert / event explanation (grounded)** — when an alert fires or an event is opened, retrieve matching samples + a summary slice for that `configId`/`category`, then LLM-narrate *why it looks risky* with cited `eventId`s. Requires alert→samples drill-down (SQL) first; LLM only packages evidence.
3. **Natural-language ask → existing APIs** — map questions like “INJECTION for config 14227 last hour” to `summary` / `timeseries` / `samples` (tool-calling). Prefer structured API/SQL results over free-form answers; vectors optional for few-shot examples of filters, not as the primary store.
4. **Optional rule/playbook Q&A** — only if a small docs corpus is added (WAF rule notes, runbooks): RAG over that KB for “what does rule X mean / what to check.” Skip until docs exist.
