# Etapa 1 — Estrutura Base do Projeto

> **Status: ✅ CONCLUÍDA**

---

## Objetivo

Criar do zero o projeto `sync-batch-data`: sincronização bidirecional incremental de dados de clientes entre múltiplas fontes externas e uma base local, usando Spring Batch 5.

---

## Stack implementada

| Tecnologia | Versão | Papel |
|---|---|---|
| Java | 21 | Runtime |
| Spring Boot | 3.4.1 | Framework base |
| Spring Batch | 5.2.1 | Orquestração de jobs |
| Spring Data JPA + JDBC | — | Persistência local e external |
| RabbitMQ | — | Trigger de jobs via fila |
| Flyway | — | Migrações de schema |
| H2 | — | Base local em dev/test |
| Resilience4j | 2.2.0 | Retry + Circuit Breaker |
| Micrometer + Prometheus | — | Métricas customizadas |
| Grafana | — | Dashboard de observabilidade |
| Lombok | — | Boilerplate |
| Springdoc OpenAPI | 2.7.0 | Swagger UI em /swagger-ui.html |

> MapStruct foi declarado opcional e não foi utilizado.

---

## Funcionalidades implementadas

### Sincronização bidirecional

- **External → Local**: lê de fonte externa via JDBC, resolve conflito pelo campo `last_updated` (aceita apenas registros mais novos que a cópia local), escreve via JPA.
- **Local → External**: lê da base local via JPA, faz pass-through (sem filtro de conflito), escreve na fonte externa via JDBC.
- Suporte a `fullLoad` (ignora data de corte) e `dateFrom` (incremental a partir de data).

### Dynamic DataSource Routing

- `AbstractRoutingDataSource` + `DataSourceContextHolder` (ThreadLocal) para troca de datasource em runtime via parâmetro `source`.
- `@Primary` aponta para `localDataSource` (JPA/Flyway/Spring Batch infra).
- `@Qualifier("routingDataSource")` usado nos Steps do batch.
- Novas fontes são adicionadas apenas em `application.yml`, sem alteração de código.

### Acionamento dos jobs

- REST: `POST /api/jobs/sync/external-to-local` e `POST /api/jobs/sync/local-to-external`
- RabbitMQ: queue `sync.trigger.queue`
- Scheduler: `@Scheduled` configurável via properties (desligado em dev por padrão)
- Parâmetros: `source`, `fullLoad`, `dateFrom`

### Entidade principal

Tabela `CLIENT`: `id`, `name`, `email`, `document`, `last_updated`, `source`

### Métricas customizadas (Micrometer)

- `sync_items_read_total`, `sync_items_written_total`, `sync_items_filtered_total`
- `sync_job_duration_ms`, `sync_retries_total`
- Fonte única de verdade: `SyncStepListener.afterStep()` usando contadores oficiais do Spring Batch.

### Resiliência

- Retry com Exponential Backoff + Jitter via Resilience4j
- Circuit Breaker por fonte externa (`CircuitBreakerRegistry`)
- Fallback retorna lista vazia e loga o evento

### Infraestrutura

- `docker-compose.yml` — app + RabbitMQ
- `docker-compose.monitoring.yml` — Prometheus + Grafana
- GitHub Actions CI: `.github/workflows/ci-build.yml`
- Grafana dashboard JSON em `monitoring/grafana/`

---

## Estrutura de pacotes criada

```
com.fidelis.syncbatch/
├── SyncBatchApplication.java
├── config/
│   ├── BatchConfig.java           (@EnableBatchProcessing, @EnableScheduling)
│   ├── DataSourceConfig.java      (local + routing datasources)
│   ├── DynamicRoutingDataSource.java
│   ├── RabbitMQConfig.java
│   └── ResilienceConfig.java
├── model/
│   └── Client.java
├── repository/
│   └── ClientRepository.java
├── reader/
│   └── ExternalClientReader.java  (@StepScope, JDBC)
├── writer/
│   ├── ExternalClientWriter.java  (JDBC upsert)
│   └── LocalClientWriter.java     (JPA save)
├── processor/
│   └── ClientSyncProcessor.java   (conflict resolution, external→local only)
├── job/
│   ├── ExternalToLocalJobConfig.java
│   └── LocalToExternalJobConfig.java
├── controller/
│   ├── SyncJobController.java
│   └── ClientController.java      (CRUD: /api/clients)
├── service/
│   ├── ExternalClientService.java
│   └── SyncService.java           (JobLauncher + JobExplorer)
├── listener/
│   └── SyncStepListener.java      (fonte única de métricas)
└── exception/
    └── GlobalExceptionHandler.java
```

---

## Bugs corrigidos durante a execução

| Bug | Causa raiz | Correção |
|---|---|---|
| `ClientSyncProcessor` filtrava todos os itens no sentido local→external | Processor consultava `clientRepository.findByEmail()` (local JPA) — timestamps sempre iguais → skip | Substituído por lambda pass-through em `LocalToExternalJobConfig` |
| Double-counting nas métricas (`sync_items_written_total = 6` para 3 writes) | `SyncStepListener` + `ExternalClientService.upsertClient()` + `LocalClientWriter.write()` todos chamavam `incrementItemsWritten` | Removidos incrementos do service e do writer; `SyncStepListener` é a única fonte |
| `SyncService.getJobExecution()` sempre retornava null | Chamava `jobRepository.getLastJobExecution()` com parâmetros inventados que nunca casavam | Injetado `JobExplorer`; usa `jobExplorer.getJobExecution(id)` |

---

## Testes

- **Unitários**: `mvn test` (excluem automaticamente o pacote `integration/`)
- **Integração**: `LiveSyncIntegrationTest` — 7 cenários ordenados, requerem app em execução
  - Usa `Assumptions.assumeTrue(appRunning)` para skip gracioso
  - Executar: `mvn test -Dtest="...LiveSyncIntegrationTest" -DfailIfNoTests=false`
  - Resultado da última execução: 11/11 PASS, read=208, written=111, 31 jobs, ~175s

> Testcontainers foi considerado mas não implementado — os testes de integração apontam para a aplicação em execução (mais representativo do ambiente real).

---

## Decisões arquiteturais importantes

- `@StepScope` em todos os beans de Step para isolamento por execução
- `@Primary` no `localDataSource` garante que JPA/Flyway/Batch infra usa sempre a base local
- `LogEventAppender` (Logback) como canal desacoplado entre o subsistema de logging e beans Spring (resolve problema de inicialização: Logback carrega antes do contexto Spring)
