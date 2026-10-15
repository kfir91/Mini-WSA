# Mini WSA

Mini WSA is a local security analytics pipeline. Clients (or the included generator) post WAF-style DLR events to **ingest-api**. Ingest validates the payload, stamps `receivedAt`, and publishes to Kafka. **analytics-service** consumes that topic, classifies the attack, scores threat, persists to PostgreSQL, and exposes query APIs.

Java 17+, Maven 3.8+, Docker Desktop, and a free local stack (PostgreSQL 16 + Kafka KRaft). No cloud databases.

## Architecture

```text
Generator / curl
        |
        v
   ingest-api :8081
        |  Kafka ack => HTTP 201
        v
 security-events.raw  ---->  analytics-service :8080
 security-events.dlq  <----  (poison / exhausted retries)
        |
        v
   PostgreSQL 16  (analytics-owned)
        |
        v
  /v1/stats/summary
  /v1/events/samples
  /v1/stats/timeseries
  /v1/alerts/*
```

| Component | Owns | Does not |
|---|---|---|
| ingest-api | HTTP ingest, Kafka produce | Database, scoring |
| analytics-service | Kafka consume, enrichment, PostgreSQL, read APIs, rate limit | Ingest HTTP |
| miniwsa-contracts | Enums + `SecurityEventMessage` | Topic names, score formula |
| generator | Synthetic traffic | Persistence |

## Repository layout

```text
miniwsa-contracts/   shared wire types
ingest-api/          POST /v1/events/ingest
analytics-service/   consumer, Flyway, query APIs
generator/           CLI attack waves
docker-compose.yml   Postgres + Kafka
.env.example         secret placeholders
```

## Run locally (Windows)

1. Copy `.env.example` to `.env` and set `POSTGRES_PASSWORD` / `SPRING_DATASOURCE_PASSWORD`.
2. Start infrastructure from the repo root:

```powershell
docker compose --env-file .env up -d
```

3. Build and test:

```powershell
mvn test
```

4. Start the services (two terminals). Load the same env vars the JVM can see (`SPRING_DATASOURCE_*`, `KAFKA_BOOTSTRAP_SERVERS=localhost:9092`).

```powershell
mvn -pl ingest-api spring-boot:run
mvn -pl analytics-service spring-boot:run
```

Kafka topics `security-events.raw` and `security-events.dlq` are created on first produce/consume (auto-create is enabled). Names come from `KAFKA_TOPIC_RAW` / `KAFKA_TOPIC_DLQ`.

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

Alerts (in-memory; lost on restart). `GET /v1/alerts/evaluate` without `alertId` checks every defined rule:

```powershell
$alert = Invoke-RestMethod -Method Post -Uri http://localhost:8080/v1/alerts/define -ContentType application/json -Body '{"configId":14227,"category":"INJECTION","metric":"event_count","window":"5m","threshold":10}'
Invoke-RestMethod http://localhost:8080/v1/alerts/evaluate
```

Stats and samples APIs are limited to **100 requests per IP per minute** (429 when exceeded). Ingest and alerts are not rate limited.

## Storage

PostgreSQL is the system of record for enriched events. It gives durable SQL aggregations (`GROUP BY`, `date_bin`, distinct IPs) without a paid warehouse. Kafka is the ingest buffer: it decouples HTTP accept from scoring and lets poison messages go to a DLQ. That split keeps ingest-api from owning a schema and keeps analytics as the only writer.

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

## What we learned

Keeping ingest Kafka-first made the HTTP contract simple: 201 is broker ack. Analytics owning PostgreSQL avoided a shared-database microservice anti-pattern. Putting the score formula in analytics (not the contracts jar) kept the wire type from becoming a business-rules dump.

## Challenges

Kafka advertised listeners on Windows Docker vs host processes. Native `date_bin` is Postgres-only, so timeseries unit tests mock the DAO and H2 is used for samples/IT. In-memory alerts are not durable across restarts.

## If we had more time

Outbox or transactional consume-to-DB, Kafka UI, durable alert store, container-native Kafka listeners for Compose app services, and a small dashboard over the summary API.
