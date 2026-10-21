# Quality report (JaCoCo + SonarQube)

Snapshot of the **last local quality run** for WSA. Regenerate after meaningful test or production changes.

| Field | Value |
|---|---|
| **Run date** | 2026-10-21 19:07 +03:00 |
| **Host** | Windows local — ASCII build copy `C:\Temp\wsa-verify` (synced from repo; Hebrew path breaks OpenAPI codegen) |
| **JDK / Maven** | Java 17.0.12 · Maven (reactor `mvn clean verify`) |
| **Command (coverage)** | `mvn clean verify` |
| **Command (Sonar)** | *not run* |
| **Compose infra** | *not part of this snapshot* |
| **Gate** | JaCoCo `check` ≥ **95%** LINE and BRANCH per module |
| **Result** | `mvn clean verify` **PASSED** |
| **Tests** | **145** run · **0** failures · **0** errors · **0** skipped |

HTML reports (after `mvn verify`):

- Aggregate: `coverage/target/site/jacoco-aggregate/index.html`
- Per module: `<module>/target/site/jacoco/index.html`

Counters below are **after configured excludes** (OpenAPI/MapStruct generated classes, `*Application*`, CLI I/O, `KafkaConsumerConfig`, Postgres-only timeseries query, ingest JPA entity / `OutboxMetricsConfig`). Compare reviewer tooling to these excludes before claiming a gap.

---

## Tests (Surefire)

| Kind | Classes (examples) | Count |
|---|---|---:|
| E2E / API (MockMvc + Spring) | `EventIngestApiE2ETest`, `AnalyticsApiE2ETest`, `AnalyticsRateLimitE2ETest` | ~32 |
| Application context | `IngestApiApplicationTest`, `AnalyticsServiceApplicationTest` | 2 |
| Unit / focused | outbox accept/relay, DLQ, services, managers, generator CLI, contracts | ~111 |
| **Total** | | **145** |

E2E coverage paths exercised: ingest outbox accept / validation / idempotent duplicate / relay publish, analytics summary/samples/timeseries/alerts, rate limit 429, Adminer-go config, demo alert fire.

---

## JaCoCo — module summary

| Module | LINE | BRANCH | INSTRUCTION | METHOD | Gate |
|---|---:|---:|---:|---:|---|
| ingest-api | **100.0%** (159/159) | **100.0%** (60/60) | 100.0% (692/692) | 100.0% (36/36) | PASS |
| analytics-service | **99.0%** (592/598) | **96.7%** (174/180) | 94.3% (3323/3525) | 87.0% (227/261) | PASS |
| generator | **100.0%** (117/117) | **97.5%** (39/40) | 100.0% (537/537) | 100.0% (33/33) | PASS |
| contracts | *skipped* (`jacoco.skip=true` — generated models only) | | | | n/a |
| **aggregate** (`coverage`) | **99.3%** (868/874) | **97.5%** (273/280) | 95.8% (4552/4754) | 89.7% (296/330) | PASS* |

\*Aggregate is report-only; the Maven gate is enforced per code module (≥95% LINE and BRANCH each).

### Aggregate counters (detail)

| Counter | Covered | Missed | Total | Ratio |
|---|---:|---:|---:|---:|
| LINE | 868 | 6 | 874 | 99.3% |
| BRANCH | 273 | 7 | 280 | 97.5% |
| INSTRUCTION | 4552 | 202 | 4754 | 95.8% |
| METHOD | 296 | 34 | 330 | 89.7% |

### Remaining misses (acceptable under gate)

| Module | Class | LINE missed | BRANCH missed |
|---|---|---:|---:|
| analytics-service | `ActionWeight` / `SeverityWeight` | 1 + 1 | 0 |
| analytics-service | `AlertDefinitionEntity` | 3 | 0 |
| analytics-service | `StatsService` | 1 | 1 |
| analytics-service | `SecurityEventSpecifications` | 0 | 2 |
| analytics-service | `AdminerGoController` | 0 | 2 |
| analytics-service | `DemoAlertFiringService` | 0 | 1 |
| generator | (one branch in CLI/wave path) | 0 | 1 |

---

## JaCoCo — package breakdown

| Module | Package | LINE | BRANCH |
|---|---|---:|---:|
| ingest-api | `com.wsa.ingest.service` | 100.0% | 100.0% |
| ingest-api | `com.wsa.ingest.controller` | 100.0% | 100.0% |
| ingest-api | `com.wsa.ingest.exception` | 100.0% | 100.0% |
| ingest-api | `com.wsa.ingest.config` | 100.0% | n/a |
| ingest-api | `com.wsa.ingest.client` | 100.0% | n/a |
| analytics-service | `com.wsa.analytics.service` | 99.4% | 94.1% |
| analytics-service | `com.wsa.analytics.domain` | 97.7% | 100.0% |
| analytics-service | `com.wsa.analytics.manager` | 100.0% | 100.0% |
| analytics-service | `com.wsa.analytics.controller` | 100.0% | 100.0% |
| analytics-service | `com.wsa.analytics.repository` | 100.0% | 96.0% |
| analytics-service | `com.wsa.analytics.mapper` | 100.0% | 100.0% |
| analytics-service | `com.wsa.analytics.web` | 100.0% | 95.5% |
| analytics-service | `com.wsa.analytics.exception` | 100.0% | n/a |
| analytics-service | `com.wsa.analytics.config` | 100.0% | n/a |
| analytics-service | `com.wsa.analytics.messaging.consumer` | 100.0% | n/a |
| analytics-service | `com.wsa.analytics.messaging.producer` | 100.0% | 100.0% |
| analytics-service | `com.wsa.analytics.entity` | 92.7% | n/a |
| generator | `com.wsa.generator` | 100.0% | 97.5% |

---

## SonarQube (local Community)

| Field | Value |
|---|---|
| **Host** | `http://localhost:9000` (Compose profile `sonar`) |
| **Project key** | `wsa` |
| **Last analysis** | **Not available** — not run in this snapshot |
| **Bugs** | — |
| **Vulnerabilities** | — |
| **Code smells** | — |
| **Security hotspots** | — |
| **Coverage (Sonar)** | — (imported from JaCoCo when analysis runs) |
| **Duplications** | — |
| **Quality gate** | — (Maven gate is JaCoCo; Sonar is UI review) |

### How to fill Sonar rows next time

```bash
docker compose --profile sonar up -d
# Wait until http://localhost:9000 is up; login admin/admin, set password, create token → .env SONAR_TOKEN=
```

```powershell
# Windows — clear stale docker-machine host first if set:
# Remove-Item Env:DOCKER_HOST,Env:DOCKER_TLS_VERIFY,Env:DOCKER_CERT_PATH -ErrorAction SilentlyContinue
mvn verify sonar:sonar "-Dsonar.token=$env:SONAR_TOKEN"
```

```bash
# macOS / Linux
mvn verify sonar:sonar "-Dsonar.token=$SONAR_TOKEN"
```

Then open the project on `:9000` and update the Sonar table above (bugs, smells, hotspots, coverage, duplications, gate status).

---

## Refresh this file

1. Sync to ASCII path if needed, then `mvn clean verify`, or `.\scripts\validate-clean-checkout.ps1` / `./scripts/validate-clean-checkout.sh`.
2. Optionally `mvn verify sonar:sonar` against local SonarQube (requires Docker Desktop running).
3. Replace the tables with the new counters / Sonar UI numbers and bump **Run date**.
