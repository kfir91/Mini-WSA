# Quality report (JaCoCo + SonarQube)

Snapshot of the **last local quality run** for Mini WSA. Regenerate after meaningful test or production changes.

| Field | Value |
|---|---|
| **Run date** | 2026-10-15 11:09 +03:00 |
| **Host** | Windows local (`C:\Temp\miniwsa-jacoco` ASCII build copy) |
| **Command (coverage)** | `mvn verify` |
| **Command (Sonar)** | *not run* — SonarQube on `:9000` was unreachable (Docker daemon / UI down) |
| **Gate** | JaCoCo `check` ≥ **95%** LINE and BRANCH per module |
| **Result** | `mvn verify` **PASSED** |

HTML reports (after `mvn verify`):

- Aggregate: `miniwsa-coverage/target/site/jacoco-aggregate/index.html`
- Per module: `<module>/target/site/jacoco/index.html`

---

## JaCoCo — module summary

Counters are after configured excludes (OpenAPI/MapStruct generated classes, `*Application*`, CLI I/O, Kafka consumer config, Postgres-only timeseries query).

| Module | LINE | BRANCH | INSTRUCTION | METHOD | CLASS | Gate |
|---|---:|---:|---:|---:|---:|---|
| ingest-api | **100.0%** (89/89) | **97.1%** (33/34) | 99.5% (398/400) | 100.0% (21/21) | 100.0% (7/7) | PASS |
| analytics-service | **99.6%** (445/447) | **100.0%** (130/130) | 96.2% (2463/2559) | 99.0% (193/195) | 100.0% (35/35) | PASS |
| generator | **100.0%** (120/120) | **97.5%** (39/40) | 100.0% (568/568) | 100.0% (33/33) | 100.0% (4/4) | PASS |
| miniwsa-contracts | *skipped* (`jacoco.skip=true` — generated models only) | | | | | n/a |
| **aggregate** (`miniwsa-coverage`) | **99.7%** (654/656) | **99.0%** (202/204) | 97.2% (3429/3527) | 99.2% (247/249) | 100.0% (46/46) | PASS* |

\*Aggregate is report-only; the Maven gate is enforced per code module (≥95% each ⇒ aggregate ≥95%).

### Aggregate counters (detail)

| Counter | Covered | Missed | Total | Ratio |
|---|---:|---:|---:|---:|
| LINE | 654 | 2 | 656 | 99.7% |
| BRANCH | 202 | 2 | 204 | 99.0% |
| INSTRUCTION | 3429 | 98 | 3527 | 97.2% |
| COMPLEXITY | 357 | 4 | 361 | 98.9% |
| METHOD | 247 | 2 | 249 | 99.2% |
| CLASS | 46 | 0 | 46 | 100.0% |

---

## JaCoCo — package breakdown

| Module | Package | LINE | BRANCH |
|---|---|---:|---:|
| ingest-api | `com.miniwsa.ingest.service` | 100.0% | 100.0% |
| ingest-api | `com.miniwsa.ingest.controller` | 100.0% | 94.4% |
| ingest-api | `com.miniwsa.ingest.exception` | 100.0% | 100.0% |
| ingest-api | `com.miniwsa.ingest.config` | 100.0% | n/a |
| ingest-api | `com.miniwsa.ingest.client` | 100.0% | n/a |
| analytics-service | `com.miniwsa.analytics.service` | 100.0% | 100.0% |
| analytics-service | `com.miniwsa.analytics.domain` | 100.0% | 100.0% |
| analytics-service | `com.miniwsa.analytics.manager` | 100.0% | 100.0% |
| analytics-service | `com.miniwsa.analytics.controller` | 100.0% | 100.0% |
| analytics-service | `com.miniwsa.analytics.repository` | 100.0% | 100.0% |
| analytics-service | `com.miniwsa.analytics.mapper` | 100.0% | 100.0% |
| analytics-service | `com.miniwsa.analytics.web` | 100.0% | 100.0% |
| analytics-service | `com.miniwsa.analytics.exception` | 100.0% | n/a |
| analytics-service | `com.miniwsa.analytics.config` | 100.0% | n/a |
| analytics-service | `com.miniwsa.analytics.messaging.consumer` | 100.0% | n/a |
| analytics-service | `com.miniwsa.analytics.messaging.producer` | 100.0% | 100.0% |
| analytics-service | `com.miniwsa.analytics.entity` | 94.4% | n/a |
| generator | `com.miniwsa.generator` | 100.0% | 97.5% |

---

## SonarQube (local Community)

| Field | Value |
|---|---|
| **Host** | `http://localhost:9000` (Compose profile `sonar`) |
| **Project key** | `miniwsa` |
| **Last analysis** | **Not available** — UI/API not reachable during this snapshot |
| **Bugs** | — |
| **Vulnerabilities** | — |
| **Code smells** | — |
| **Security hotspots** | — |
| **Coverage (Sonar)** | — (imported from JaCoCo when analysis runs) |
| **Duplications** | — |
| **Quality gate** | — (Maven gate is JaCoCo; Sonar is UI review) |

### How to fill Sonar rows next time

```powershell
docker compose --profile sonar up -d
# Wait until http://localhost:9000 is up; login admin/admin, set password, create token → .env SONAR_TOKEN=

mvn verify sonar:sonar "-Dsonar.token=$env:SONAR_TOKEN"
```

Then open the project on `:9000` and update the Sonar table above (bugs, smells, hotspots, coverage, duplications, gate status).

---

## Refresh this file

1. `mvn verify` (from an ASCII path if needed).
2. Optionally `mvn verify sonar:sonar` against local SonarQube.
3. Replace the tables with the new counters / Sonar UI numbers and bump **Run date**.
