# Etapa 2 — Evolução da Modelagem (Metadata-Driven)

> **Status: ⏸ NÃO EXECUTADA — reservada como evolução futura**

---

## Por que não foi executada

Após concluir a Etapa 1, o foco foi direcionado para **observabilidade e dashboard em tempo real** (Etapas 3 e 4), que agregam mais valor imediato ao projeto. A modelagem metadata-driven adiciona complexidade arquitetural significativa e é mais relevante quando o número de tabelas/fontes sincronizadas crescer.

A entidade `Client` simples continua sendo o modelo ativo do projeto.

---

## Objetivo original

Refatorar para arquitetura **metadata-driven**, onde as regras de sincronização (quais tabelas, em qual direção, com que frequência) são configuradas em banco de dados — sem alteração de código para adicionar novas tabelas.

---

## Escopo planejado

### Novas entidades

```
ExternalSystem          — representa uma fonte externa (SAP, Oracle, etc.)
  └── SyncTableMapping  — qual tabela sincronizar, direção, estratégia, cron
        ├── SyncExecution   — histórico de cada execução (read/write/status/duration)
        └── WebhookConfiguration — notificação pós-sync
```

**Relacionamentos:**
- `ExternalSystem` 1 → N `SyncTableMapping`
- `SyncTableMapping` 1 → N `SyncExecution`
- `SyncTableMapping` 1 → N `WebhookConfiguration`

### Enums necessários

```java
SyncDirection  { BIDIRECTIONAL, EXTERNAL_TO_LOCAL, LOCAL_TO_EXTERNAL }
SyncStrategy   { INCREMENTAL, FULL_LOAD, CDC, EVENT_DRIVEN }
SyncStatus     { SUCCESS, FAILED, RUNNING, PARTIAL_SUCCESS }
```

### Impacto nos componentes existentes

| Componente | Mudança necessária |
|---|---|
| `ExternalClientReader` (→ `DynamicTableReader`) | Ler tabela/query de `SyncTableMapping` em vez de hardcoded |
| `ClientSyncProcessor` | Generalizar para qualquer entidade via mapa de campos |
| `ExternalToLocalJobConfig` | Receber `mappingId` como parâmetro |
| `SyncService` | Ler mappings ativos e disparar jobs conforme `cronExpression` |
| `SyncJobController` | Aceitar `mappingId` ou `externalSystemCode + tableName` |
| Flyway | Nova migração `V2__create_metadata_tables.sql` |

---

## Pré-requisitos para execução

- Etapas 1, 3 e 4 concluídas (projeto estável com observabilidade funcional)
- Definição clara de quais fontes/tabelas precisam ser configuráveis dinamicamente
- Decisão sobre estratégia de mapeamento de schema (colunas fixas vs. dinâmicas via reflection/JDBC metadata)

---

## Estimativa de esforço

Alta — afeta Reader, Processor, Writer, Controller, Service, Scheduler e requer migração de dados existentes.

---

## Quando executar

Quando o projeto precisar suportar mais de 2-3 tabelas sincronizadas ou quando a adição de novas fontes exigir deploy de código. Até lá, o modelo baseado em `Client` + `source` (parâmetro de job) é suficiente.
