# sync-batch-data

Spring Batch 5 project for **bidirectional incremental synchronisation** of CLIENT data between multiple external datasources and a local database, with real-time intelligent monitoring.

---

## Architecture

```
┌──────────────────────────────────────────────────────────────────────┐
│                           TRIGGERS                                   │
│  ┌──────────────┐   ┌──────────────────┐   ┌──────────────────┐     │
│  │  REST API    │   │    RabbitMQ      │   │   @Scheduled     │     │
│  │ /api/jobs/.. │   │ sync.trigger.q   │   │  (every 2h)      │     │
│  └──────┬───────┘   └────────┬─────────┘   └────────┬─────────┘     │
│         └───────────────────▼───────────────────────┘               │
│                         SyncService                                  │
│               ┌────────────────────────────────┐                    │
│               │  externalToLocalSyncJob        │                    │
│               │  localToExternalSyncJob        │                    │
│               └────────────┬───────────────────┘                    │
└────────────────────────────┼───────────────────────────────────────┘
                             │
               ┌─────────────▼──────────────┐
               │   Spring Batch 5 Steps     │
               │  Reader → Processor → Writer│
               └─────────────┬──────────────┘
                             │
          ┌──────────────────┼──────────────────┐
          ▼                  ▼                  ▼
  ExternalClientReader  ClientSync        LocalClientWriter
  (JDBC - external DB)  Processor         (JPA - local DB)
        │             (conflict resolve)        │
        └─────── Resilience4j ─────────────────┘
                 Circuit Breaker + Retry

                             │
               ┌─────────────▼──────────────────────┐
               │   Real-time Monitoring Agent       │
               │                                    │
               │  LogEventAppender (Logback)        │
               │       → BlockingQueue              │
               │       → PerformanceAnalyzer (1s)  │
               │       → IntelligentAgent (rules)  │
               │       → MonitorBroadcaster (SSE)  │
               │       → /monitor/live (dashboard) │
               └────────────────────────────────────┘
```

---

## Quick Start

### 1. Start infrastructure

```bash
# RabbitMQ (required for message-triggered sync)
docker compose up -d

# Grafana + Prometheus (optional — metrics observability)
docker compose -f docker-compose.monitoring.yml up -d
```

### 2. Run the application

```bash
mvn spring-boot:run
```

| Service    | URL                                        | Credentials   |
|------------|--------------------------------------------|---------------|
| App        | http://localhost:8080/swagger-ui.html      | —             |
| Live Monitor | http://localhost:8080/monitor/live       | —             |
| Grafana    | http://localhost:3000                      | admin / admin |
| Prometheus | http://localhost:9090                      | —             |
| RabbitMQ   | http://localhost:15672                     | guest / guest |

---

## REST API

### Sync jobs

```bash
# External → Local (incremental from a date)
curl -X POST http://localhost:8080/api/jobs/sync/external-to-local \
  -H "Content-Type: application/json" \
  -d '{"source":"source1","fullLoad":false,"dateFrom":"2026-01-01T00:00:00"}'

# External → Local (full load)
curl -X POST http://localhost:8080/api/jobs/sync/external-to-local \
  -H "Content-Type: application/json" \
  -d '{"source":"source1","fullLoad":true}'

# Local → External
curl -X POST http://localhost:8080/api/jobs/sync/local-to-external \
  -H "Content-Type: application/json" \
  -d '{"source":"source1","fullLoad":true}'

# Job status by execution ID
curl http://localhost:8080/api/jobs/status/1
```

### Client data

```bash
# List all clients
curl http://localhost:8080/api/clients

# Create / upsert client
curl -X POST http://localhost:8080/api/clients \
  -H "Content-Type: application/json" \
  -d '{"name":"Alice","email":"alice@example.com","source":"local"}'

# Delete client by email
curl -X DELETE http://localhost:8080/api/clients/alice@example.com

# Count clients
curl http://localhost:8080/api/clients/count
```

### Monitoring

```bash
# Live dashboard (browser)
open http://localhost:8080/monitor/live

# SSE stream of snapshots (~1 s interval)
curl -N http://localhost:8080/monitor/stream

# Current snapshot as JSON (polling fallback)
curl http://localhost:8080/monitor/status

# Spring Actuator (Prometheus metrics)
curl http://localhost:8080/actuator/prometheus
```

---

## Adding a New Datasource

1. Add JDBC driver to `pom.xml`.
2. Add entry under `datasources.external` in `application.yml`:
   ```yaml
   mssql-prod:
     url: jdbc:sqlserver://host:1433;databaseName=ClientsDB
     username: ${DB_USER}
     password: ${DB_PASS}
     driver-class-name: com.microsoft.sqlserver.jdbc.SQLServerDriver
   ```
3. Trigger sync: `{"source":"mssql-prod","fullLoad":false}` — no code changes needed.

---

## Tests

### Unit tests

```bash
mvn test
```

Integration tests are excluded from the default test run (configured in `pom.xml` Surefire excludes).

### Integration tests

Integration tests require the application to be **running** (`mvn spring-boot:run`) before execution. If the app is not reachable, all scenarios are gracefully skipped via `Assumptions.assumeTrue`.

```bash
mvn test -Dtest="com.fidelis.syncbatch.integration.LiveSyncIntegrationTest" -DfailIfNoTests=false
```

#### Test scenarios

| # | Scenario | Jobs | Assertion |
|---|----------|------|-----------|
| 1 | Full sync local → source1 + source2 | 2 | read ≥ 1, written ≥ 1 |
| 2 | source1 → local conflict resolution (no newer data) | 1 | filterCount > 0, writeCount = 0 |
| 3 | Create 5 new clients, sync to both sources | 2 | written ≥ 5 per source |
| 4 | Update 2 clients (future timestamp), incremental sync | 2 | written ≥ 2 |
| 5 | source2 → local conflict resolution | 1 | filterCount > 0 |
| 6 | Load test — 5 rounds × 4 jobs (16 s Prometheus delay per round) | 20 | cumulative metrics increase |
| 7 | Final metrics summary printed to console | — | read=208, written=111 |

#### Last run results

```
Tests run: 11, Failures: 0, Errors: 0, Skipped: 0
Time elapsed: ~175 s
BUILD SUCCESS

Scenario 1  → read=15,  written=12
Scenario 2  → read=18,  written=12  (conflict: 3 filtered)
Scenario 3  → read=34,  written=28
Scenario 4  → read=40,  written=31
Scenario 5  → read=48,  written=31  (conflict: all filtered)
Load round 1/5 (jobs 12-15)  → read=80,   written=47
Load round 2/5 (jobs 16-19)  → read=112,  written=63
Load round 3/5 (jobs 20-23)  → read=144,  written=79
Load round 4/5 (jobs 24-27)  → read=176,  written=95
Load round 5/5 (jobs 28-31)  → read=208,  written=111
FINAL: read=208, written=111, 31 jobs completed, 0 skipped
```

After the load test, metrics are visible in:
- **Grafana** → Spring Batch dashboard at http://localhost:3000
- **Live Monitor** → http://localhost:8080/monitor/live (real-time SSE feed)
- **Prometheus** → http://localhost:9090/graph?g0.expr=sync_items_written_total

---

## Real-time Monitoring Agent

The live dashboard at `/monitor/live` is driven by an embedded intelligent agent:

```
Logback → LogEventAppender (BlockingQueue<8000>)
              ↓  drained every 1 s
         PerformanceAnalyzer (@Scheduled)
              ↓  regex-parsed into SyncEvents
         IntelligentAgent (rule-based)
              ↓  Portuguese comments by event type
         MonitorBroadcaster (SSE / CopyOnWriteArrayList)
              ↓
         Browser (EventSource API, auto-reconnect)
```

**Metrics tracked:** total read, written, filtered, skipped · jobs completed/failed · throughput (rec/s) · avg/last duration · per-source status · recent agent comments · recent log feed.

**Agent rules (IntelligentAgent):**

| Event | Comment generated |
|-------|-------------------|
| JOB_STARTED | "Iniciando sincronização {direction}" |
| JOB_COMPLETED (no data) | "Nenhum dado encontrado na fonte …" |
| JOB_COMPLETED (already synced) | "Dados de … já estão sincronizados" |
| JOB_COMPLETED (wrote data) | "Sincronização … concluída com {perf label} — N registros em Xms" |
| JOB_FAILED | "Falha na sincronização da fonte …" |
| STEP_COMPLETED (>80% filtered) | "Alta taxa de filtragem (N%)" |
| STEP_COMPLETED (>100 written) | "Volume alto processado: N registros" |
| FETCH_COMPLETED (0 records) | "Nenhum registro retornado pela fonte …" |
| FETCH_COMPLETED (>50 records) | "Lote grande recebido: N registros" |
| CIRCUIT_BREAKER | "Circuit breaker ativado para a fonte …" |
| SCHEMA_INIT | "Schema inicializado com sucesso para …" |
| ERROR | "Erro detectado: {truncated message}" |

---

## Technologies

| Layer | Technology |
|-------|-----------|
| Runtime | Java 21 |
| Framework | Spring Boot 3.4, Spring Batch 5 |
| Persistence | Spring Data JPA, Flyway, H2 (local), H2 (external simulation) |
| Messaging | RabbitMQ (AMQP) |
| Resilience | Resilience4j (circuit breaker, retry) |
| Monitoring | Prometheus, Grafana, Spring Actuator |
| Real-time UI | Thymeleaf, SSE (SseEmitter), custom Logback appender |
| API Docs | SpringDoc OpenAPI (Swagger UI) |
| Tests | JUnit 5, Mockito, RestAssured (integration) |
